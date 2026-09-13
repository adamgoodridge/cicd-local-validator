package net.adamgoodridge.cicd_local_validator.web;

import net.adamgoodridge.cicd_local_validator.domain.JobResult;
import net.adamgoodridge.cicd_local_validator.domain.JobResultStatus;
import net.adamgoodridge.cicd_local_validator.domain.PipelineResultStatus;
import net.adamgoodridge.cicd_local_validator.domain.ValidationRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidationController.class)
class ValidationControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ValidationController controller;

	@Test
	void validatesPostedPipelineYaml() throws Exception {
		mockMvc.perform(post("/api/validation")
				.contentType(MediaType.TEXT_PLAIN)
				.content("stages: [test]\nunit:\n  image: alpine\n  script: echo test\n"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.issues").isEmpty());
	}

	@Test
	void refusesToRunInvalidPipelineLocally() throws Exception {
		mockMvc.perform(post("/api/validation/run/local")
				.contentType(MediaType.TEXT_PLAIN)
				.content("stages: [test]\nunit:\n  script: echo test\n"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.issues").isNotEmpty());
	}

	@Test
	void runsValidPipelineLocally() throws Exception {
		mockMvc.perform(post("/api/validation/run/local")
				.contentType(MediaType.TEXT_PLAIN)
				.content("stages: [test]\nunit:\n  image: alpine\n  script: echo local\n"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pipelineStatus").value("PASSED"))
			.andExpect(jsonPath("$.jobResults[0].status").value("PASSED"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void exportsJobLogForKnownRun() throws Exception {
		UUID runId = UUID.randomUUID();
		ValidationRun run = new ValidationRun(
				runId,
				List.of(new JobResult("unit", JobResultStatus.PASSED, 0, "hello log")),
				PipelineResultStatus.PASSED);

		Map<UUID, ValidationRun> runsById =
				(Map<UUID, ValidationRun>) ReflectionTestUtils.getField(controller, "runsById");
		runsById.put(runId, run);

		mockMvc.perform(get("/api/validation/runs/{runId}/jobs/{jobName}/log", runId, "unit"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString(".log\"")))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andExpect(content().string("hello log"));
	}

	@Test
	void returnsNotFoundWhenExportingUnknownRunLog() throws Exception {
		mockMvc.perform(get("/api/validation/runs/{runId}/jobs/{jobName}/log", UUID.randomUUID(), "unit"))
				.andExpect(status().isNotFound());
	}

	@Test
	@SuppressWarnings("unchecked")
	void returnsNotFoundWhenExportingUnknownJobLog() throws Exception {
		UUID runId = UUID.randomUUID();
		ValidationRun run = new ValidationRun(
				runId,
				List.of(new JobResult("build", JobResultStatus.PASSED, 0, "done")),
				PipelineResultStatus.PASSED);
		Map<UUID, ValidationRun> runsById =
				(Map<UUID, ValidationRun>) ReflectionTestUtils.getField(controller, "runsById");
		runsById.put(runId, run);

		mockMvc.perform(get("/api/validation/runs/{runId}/jobs/{jobName}/log", runId, "unit"))
				.andExpect(status().isNotFound());
	}

	@Test
	@SuppressWarnings("unchecked")
	void listsJobsForKnownRun() throws Exception {
		UUID runId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		String buildId = UUID.nameUUIDFromBytes((runId + "|build").getBytes(StandardCharsets.UTF_8)).toString();
		String testId = UUID.nameUUIDFromBytes((runId + "|test").getBytes(StandardCharsets.UTF_8)).toString();
		ValidationRun run = new ValidationRun(
				runId,
				List.of(
						new JobResult("build", JobResultStatus.PASSED, 0, "done"),
						new JobResult("test", JobResultStatus.PASSED, 0, "ok")),
				PipelineResultStatus.PASSED);
		Map<UUID, ValidationRun> runsById =
				(Map<UUID, ValidationRun>) ReflectionTestUtils.getField(controller, "runsById");
		runsById.put(runId, run);

		mockMvc.perform(get("/api/validation/runs/{runId}/jobs", runId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(buildId))
				.andExpect(jsonPath("$[0].name").value("build"))
				.andExpect(jsonPath("$[1].id").value(testId))
				.andExpect(jsonPath("$[1].name").value("test"));
	}

	@Test
	void returnsNotFoundWhenListingJobsForUnknownRun() throws Exception {
		mockMvc.perform(get("/api/validation/runs/{runId}/jobs", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}
}
