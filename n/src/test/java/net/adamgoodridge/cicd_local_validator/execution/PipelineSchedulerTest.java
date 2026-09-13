package net.adamgoodridge.cicd_local_validator.execution;

import net.adamgoodridge.cicd_local_validator.ErrorMessages;
import net.adamgoodridge.cicd_local_validator.domain.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSchedulerTest {
	@Test
	void runsDependenciesInOrderAndSkipsFailedDependents() {
		var pipeline = new PipelineDefinition(
				List.of("build", "test"),
				List.of(),
				List.of(
						job("compile", "build", List.of(), false),
						job("unit", "test", List.of("compile"), false),
						job("report", "test", List.of("compile"), true)));
		List<String> executionOrder = new ArrayList<>();
		DockerJobExecutor executor = (job, variables) -> {
			executionOrder.add(job.name());
			assertThat(variables).isEmpty();
			return new JobResult(job.name(),
					"compile".equals(job.name()) ? JobResultStatus.FAILED : JobResultStatus.PASSED,
					"compile".equals(job.name()) ? 1 : 0,
					"compile".equals(job.name()) ? "failed" : "passed");
		};

		var run = new PipelineScheduler().execute(pipeline, executor);

		assertThat(executionOrder).containsExactly("compile", "report");
		assertThat(run.pipelineStatus()).isEqualTo(PipelineResultStatus.FAILED);
		assertThat(run.jobResults()).extracting(JobResult::status)
				.containsExactly(JobResultStatus.FAILED, JobResultStatus.SKIPPED, JobResultStatus.PASSED);
		assertThat(run.jobResults().get(1).message()).isEqualTo(ErrorMessages.JOB_REQUIRED_DEPENDENCY_FAILED);
	}

	@Test
	void blocksUnschedulableJobsWithSharedErrorMessage() {
		var pipeline = new PipelineDefinition(
				List.of("test"),
				List.of(),
				List.of(
						job("build", "test", List.of("test"), false),
						job("test", "test", List.of("build"), false)));

		var run = new PipelineScheduler().execute(pipeline,
				(job, variables) -> {
					assertThat(variables).isEmpty();
					return new JobResult(job.name(), JobResultStatus.PASSED, 0, "passed");
				});

		assertThat(run.pipelineStatus()).isEqualTo(PipelineResultStatus.BLOCKED);
		assertThat(run.jobResults()).hasSize(2).allSatisfy(result -> {
			assertThat(result.status()).isEqualTo(JobResultStatus.BLOCKED);
			assertThat(result.message()).isEqualTo(ErrorMessages.JOB_COULD_NOT_BE_SCHEDULED);
		});
	}

	@Test
	void jobVariablesOverridePipelineVariables() {
		var pipeline = new PipelineDefinition(
				List.of("test"),
				List.of(new YamlVariable("MODE", "pipeline")),
				List.of(job("unit", "test", List.of(), false)));
		var observed = new ArrayList<String>();

		new PipelineScheduler().execute(pipeline, (job, variables) -> {
			observed.add(variables.get("MODE"));
			return new JobResult(job.name(), JobResultStatus.PASSED, 0, "passed");
		});

		assertThat(observed).containsExactly("pipeline");
	}

	private JobDefinition job(String name, String stage, List<String> needs, boolean alwaysRun) {
		return new JobDefinition(name, "alpine:3.20", List.of("echo " + name), stage, needs, List.of(), null,
				false, alwaysRun);
	}
}
