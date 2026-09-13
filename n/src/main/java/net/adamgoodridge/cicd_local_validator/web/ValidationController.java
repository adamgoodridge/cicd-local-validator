package net.adamgoodridge.cicd_local_validator.web;

import net.adamgoodridge.cicd_local_validator.domain.StaticValidationResult;
import net.adamgoodridge.cicd_local_validator.domain.ValidationRun;
import net.adamgoodridge.cicd_local_validator.execution.LocalJobExecutor;
import net.adamgoodridge.cicd_local_validator.execution.PipelineScheduler;
import net.adamgoodridge.cicd_local_validator.pipeline.PipelineParser;
import net.adamgoodridge.cicd_local_validator.validation.PipelineValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/validation")
public class ValidationController {
	private final PipelineValidator validator;
	private final PipelineParser parser;
	private final PipelineScheduler scheduler;
	private final Path localWorkspace;
	private final Map<UUID, ValidationRun> runsById;

	public ValidationController(
			@Value("${cicd.execution.local.workspace:${user.dir}}") String localWorkspace) {
		this.parser = new PipelineParser();
		this.validator = new PipelineValidator(parser);
		this.scheduler = new PipelineScheduler();
		this.localWorkspace = Path.of(localWorkspace).toAbsolutePath().normalize();
		this.runsById = new ConcurrentHashMap<>();
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
		ValidationRun run = scheduler.execute(
				parser.parse(source).pipeline(), new LocalJobExecutor(localWorkspace));
		runsById.put(run.id(), run);
		return ResponseEntity.ok(run);
	}

	@GetMapping(value = "/runs/{runId}/jobs/{jobName}/log", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> exportJobLog(@PathVariable UUID runId, @PathVariable String jobName) {
		ValidationRun run = runsById.get(runId);
		if (run == null) {
			return ResponseEntity.notFound().build();
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
	public ResponseEntity<List<JobReference>> jobs(@PathVariable UUID runId) {
		ValidationRun run = runsById.get(runId);
		if (run == null) {
			return ResponseEntity.notFound().build();
		}
		List<JobReference> jobs = run.jobResults().stream()
				.map(jobResult -> new JobReference(jobId(run.id(), jobResult.jobName()), jobResult.jobName()))
				.toList();
		return ResponseEntity.ok(jobs);
	}

	private UUID jobId(UUID runId, String jobName) {
		return UUID.nameUUIDFromBytes((runId + "|" + jobName).getBytes(StandardCharsets.UTF_8));
	}

	private String sanitizeSegment(String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private record JobReference(UUID id, String name) {
	}
}
