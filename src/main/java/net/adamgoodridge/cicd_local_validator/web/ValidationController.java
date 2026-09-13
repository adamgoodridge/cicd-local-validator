package net.adamgoodridge.cicd_local_validator.web;

import net.adamgoodridge.cicd_local_validator.domain.PipelineResultStatus;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	private final ExecutorService executorService;

	public ValidationController(
			@Value("${cicd.execution.local.workspace:${user.dir}}") String localWorkspace) {
		this.parser = new PipelineParser();
		this.validator = new PipelineValidator(parser);
		this.scheduler = new PipelineScheduler();
		this.localWorkspace = Path.of(localWorkspace).toAbsolutePath().normalize();
		this.runsById = new ConcurrentHashMap<>();
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
		UUID runId = UUID.randomUUID();
		CompletableFuture<ValidationRun> future = CompletableFuture.supplyAsync(
				() -> scheduler.execute(parser.parse(source).pipeline(), new LocalJobExecutor(localWorkspace)),
				executorService);
		runsById.put(runId, future);
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
		List<JobReference> jobs = run.jobResults().stream()
				.map(jobResult -> new JobReference(jobId(run.id(), jobResult.jobName()), jobResult.jobName()))
				.toList();
		return ResponseEntity.ok(jobs);
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

	private record JobReference(UUID id, String name) {
	}
}
