package net.adamgoodridge.cicd_local_validator.web;

import net.adamgoodridge.cicd_local_validator.domain.PipelineResultStatus;
import net.adamgoodridge.cicd_local_validator.domain.PipelineDefinition;
import net.adamgoodridge.cicd_local_validator.domain.StaticValidationResult;
import net.adamgoodridge.cicd_local_validator.domain.ValidationRun;
import net.adamgoodridge.cicd_local_validator.execution.LocalJobExecutor;
import net.adamgoodridge.cicd_local_validator.execution.PipelineScheduler;
import net.adamgoodridge.cicd_local_validator.pipeline.PipelineParser;
import net.adamgoodridge.cicd_local_validator.validation.PipelineValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/validation")
public class ValidationController {
	private static final String UNSAFE_FILENAME_SEGMENT_PATTERN = "[^a-zA-Z0-9._-]";
	private static final String JOB_ID_SEPARATOR = "|";
	private static final String FILENAME_SEGMENT_REPLACEMENT = "_";

	private final PipelineValidator validator;
	private final PipelineParser parser;
	private final PipelineScheduler scheduler;
	private final Path localWorkspace;
	private final Map<UUID, CompletableFuture<ValidationRun>> runsById;
	private final Map<UUID, PipelineDefinition> pipelinesByRunId;
	private final ExecutorService executorService;

	public ValidationController(
			@Value("${cicd.execution.local.workspace:${user.dir}}") String localWorkspace) {
		this.parser = new PipelineParser();
		this.validator = new PipelineValidator(parser);
		this.scheduler = new PipelineScheduler();
		this.localWorkspace = Path.of(localWorkspace).toAbsolutePath().normalize();
		this.runsById = new ConcurrentHashMap<>();
		this.pipelinesByRunId = new ConcurrentHashMap<>();
		this.executorService = Executors.newCachedThreadPool();
	}

	@PostMapping(consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml"},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public StaticValidationResult validate(@RequestBody String pipeline) {
		return validator.validate(pipeline);
	}

	@PostMapping(value = "/run/local", consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml"},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> runLocally(@RequestBody String source) {
		StaticValidationResult validation = validator.validate(source);
		if (!validation.isValid()) {
			return ResponseEntity.badRequest().body(validation);
		}
		PipelineDefinition pipeline = parser.parse(source).pipeline();
		UUID runId = UUID.randomUUID();
		Path runWorkspace = localWorkspace.resolve(".cicd-local-validator").resolve(runId.toString());
		CompletableFuture<ValidationRun> future = CompletableFuture.supplyAsync(
				() -> scheduler.execute(pipeline, new LocalJobExecutor(localWorkspace, runWorkspace)),
				executorService);
		runsById.put(runId, future);
		pipelinesByRunId.put(runId, pipeline);
		return ResponseEntity.accepted().body(new RunStatus(runId, PipelineResultStatus.RUNNING));
	}

	@GetMapping(value = "/runs/{runId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> runStatus(@PathVariable UUID runId) {
		CompletableFuture<ValidationRun> future = runsById.get(runId);
		if (future == null) {
			return ResponseEntity.notFound().build();
		}
		if (!future.isDone()) {
			return ResponseEntity.ok(new RunStatus(runId, PipelineResultStatus.RUNNING));
		}
		ValidationRun run = getCompleted(future);
		if (run == null) {
			return ResponseEntity.internalServerError().build();
		}
		return ResponseEntity.ok(new RunStatus(runId, run.pipelineStatus()));
	}

	@GetMapping(value = "/runs/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> runResult(@PathVariable UUID runId) {
		CompletableFuture<ValidationRun> future = runsById.get(runId);
		if (future == null) {
			return ResponseEntity.notFound().build();
		}
		if (!future.isDone()) {
			return ResponseEntity.accepted().body(new RunStatus(runId, PipelineResultStatus.RUNNING));
		}
		ValidationRun run = getCompleted(future);
		if (run == null) {
			return ResponseEntity.internalServerError().build();
		}
		return ResponseEntity.ok(run);
	}

	@GetMapping(value = "/runs/{runId}/jobs/{jobName}/log", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> exportJobLog(@PathVariable UUID runId, @PathVariable String jobName) {
		CompletableFuture<ValidationRun> future = runsById.get(runId);
		if (future == null) {
			return ResponseEntity.notFound().build();
		}
		if (!future.isDone()) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).build();
		}
		ValidationRun run = getCompleted(future);
		if (run == null) {
			return ResponseEntity.internalServerError().build();
		}
		return run.jobResults().stream()
				.filter(jobResult -> jobResult.jobName().equals(jobName))
				.findFirst()
				.map(jobResult -> ResponseEntity.ok()
						.contentType(MediaType.TEXT_PLAIN)
						.header(HttpHeaders.CONTENT_DISPOSITION,
								"attachment; filename=\"" + runId + "-" + sanitizeSegment(jobResult.jobName()) + ".log\"")
						.body(jobResult.message() == null ? "" : jobResult.message()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/runs/{runId}/jobs/{jobName}/artifacts", produces = "application/zip")
	public ResponseEntity<?> exportJobArtifacts(@PathVariable UUID runId, @PathVariable String jobName) {
		CompletableFuture<ValidationRun> future = runsById.get(runId);
		if (future == null) {
			return ResponseEntity.notFound().build();
		}
		if (!future.isDone()) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).build();
		}
		if (getCompleted(future) == null) {
			return ResponseEntity.internalServerError().build();
		}
		PipelineDefinition pipeline = pipelinesByRunId.get(runId);
		if (pipeline == null) {
			return ResponseEntity.notFound().build();
		}
		return pipeline.jobs().stream()
				.filter(job -> job.name().equals(jobName) && job.artifacts() != null)
				.findFirst()
				.map(job -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType("application/zip"))
						.header(HttpHeaders.CONTENT_DISPOSITION,
								"attachment; filename=\"" + runId + "-" + sanitizeSegment(jobName) + "-artifacts.zip\"")
						.body(zipArtifacts(runWorkspace(runId, jobName), job.artifacts().paths())))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/runs/{runId}/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> jobs(@PathVariable UUID runId) {
		CompletableFuture<ValidationRun> future = runsById.get(runId);
		if (future == null) {
			return ResponseEntity.notFound().build();
		}
		if (!future.isDone()) {
			return ResponseEntity.accepted().body(new RunStatus(runId, PipelineResultStatus.RUNNING));
		}
		ValidationRun run = getCompleted(future);
		if (run == null) {
			return ResponseEntity.internalServerError().build();
		}
		PipelineDefinition pipeline = pipelinesByRunId.get(runId);
		List<JobReference> jobs = run.jobResults().stream()
				.map(jobResult -> new JobReference(jobId(run.id(), jobResult.jobName()), jobResult.jobName(),
						pipeline != null && pipeline.jobs().stream().anyMatch(job ->
								job.name().equals(jobResult.jobName()) && job.artifacts() != null)))
				.toList();
		return ResponseEntity.ok(jobs);
	}

	private byte[] zipArtifacts(Path workspace, List<String> artifactPaths) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
			for (String artifactPath : artifactPaths) {
				Path path = workspace.resolve(artifactPath).normalize();
				if (!path.startsWith(workspace) || !Files.exists(path)) {
					continue;
				}
				try (var files = Files.walk(path)) {
					for (Path file : files.filter(Files::isRegularFile).toList()) {
						zip.putNextEntry(new ZipEntry(workspace.relativize(file).toString().replace('\\', '/')));
						Files.copy(file, zip);
						zip.closeEntry();
					}
				}
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to archive Job artifacts.", exception);
		}
	}

	private Path runWorkspace(UUID runId, String jobName) {
		Path workspace = localWorkspace.resolve(".cicd-local-validator").resolve(runId.toString()).resolve(jobName).normalize();
		if (!workspace.startsWith(localWorkspace)) {
			throw new IllegalArgumentException("Invalid Job name for artifact workspace.");
		}
		return workspace;
	}

	private ValidationRun getCompleted(CompletableFuture<ValidationRun> future) {
		try {
			return future.get();
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException _) {
			return null;
		}
	}

	private UUID jobId(UUID runId, String jobName) {
		return UUID.nameUUIDFromBytes((runId + JOB_ID_SEPARATOR + jobName).getBytes(StandardCharsets.UTF_8));
	}

	private String sanitizeSegment(String value) {
		return value.replaceAll(UNSAFE_FILENAME_SEGMENT_PATTERN, FILENAME_SEGMENT_REPLACEMENT);
	}

	private record JobReference(UUID id, String name, boolean hasArtifacts) {
	}
}
