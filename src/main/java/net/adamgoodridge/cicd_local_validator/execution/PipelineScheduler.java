package net.adamgoodridge.cicd_local_validator.execution;

import net.adamgoodridge.cicd_local_validator.ErrorMessages;
import net.adamgoodridge.cicd_local_validator.domain.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PipelineScheduler {
	public ValidationRun execute(PipelineDefinition pipeline, DockerJobExecutor executor) {
		Map<String, JobDefinition> jobs = indexJobs(pipeline);
		Map<String, JobResult> results = new LinkedHashMap<>();
		Set<String> remaining = new HashSet<>(jobs.keySet());
		Map<String, String> pipelineVariables = variables(pipeline.yamlVariables());
		executeJobsUntilComplete(pipeline, executor, jobs, results, remaining, pipelineVariables);

		PipelineResultStatus pipelineStatus = pipelineStatus(results);
		return new ValidationRun(UUID.randomUUID(), new ArrayList<>(results.values()), pipelineStatus);
	}

	private void executeJobsUntilComplete(
			PipelineDefinition pipeline,
			DockerJobExecutor executor,
			Map<String, JobDefinition> jobs,
			Map<String, JobResult> results,
			Set<String> remaining,
			Map<String, String> pipelineVariables) {
		while (!remaining.isEmpty()) {
			boolean progressed = executeEligibleJobs(pipeline, executor, jobs, results, remaining, pipelineVariables);
			if (!progressed) {
				blockRemainingJobs(results, remaining);
			}
		}
	}

	private Map<String, JobDefinition> indexJobs(PipelineDefinition pipeline) {
		Map<String, JobDefinition> jobs = new LinkedHashMap<>();
		for (JobDefinition job : pipeline.jobs()) {
			jobs.put(job.name(), job);
		}
		return jobs;
	}

	private boolean executeEligibleJobs(
			PipelineDefinition pipeline,
			DockerJobExecutor executor,
			Map<String, JobDefinition> jobs,
			Map<String, JobResult> results,
			Set<String> remaining,
			Map<String, String> pipelineVariables) {
		boolean progressed = false;
		for (JobDefinition job : pipeline.jobs()) {
			if (!remaining.contains(job.name()) || !eligible(job, jobs, results, pipeline)) {
				continue;
			}
			JobResult result = executeJob(job, executor, pipelineVariables, results);
			results.put(job.name(), result);
			remaining.remove(job.name());
			progressed = true;
		}
		return progressed;
	}

	private void blockRemainingJobs(Map<String, JobResult> results, Set<String> remaining) {
		for (String name : remaining) {
			results.put(name, new JobResult(name, JobResultStatus.BLOCKED, null,
					ErrorMessages.JOB_COULD_NOT_BE_SCHEDULED));
		}
		remaining.clear();
	}

	private PipelineResultStatus pipelineStatus(Map<String, JobResult> results) {
		if (results.values().stream().anyMatch(result -> result.status() == JobResultStatus.FAILED)) {
			return PipelineResultStatus.FAILED;
		}
		if (results.values().stream().anyMatch(result -> result.status() == JobResultStatus.BLOCKED)) {
			return PipelineResultStatus.BLOCKED;
		}
		return PipelineResultStatus.PASSED;
	}

	private JobResult executeJob(
			JobDefinition job,
			DockerJobExecutor executor,
			Map<String, String> pipelineVariables,
			Map<String, JobResult> results) {
		if (hasFailedDependency(job, results) && !job.alwaysRun()) {
			return new JobResult(job.name(), JobResultStatus.SKIPPED, null,
					ErrorMessages.JOB_REQUIRED_DEPENDENCY_FAILED);
		}
		Map<String, String> variables = new HashMap<>(pipelineVariables);
		for (YamlVariable yamlVariable : job.yamlVariables()) {
			variables.put(yamlVariable.name(), yamlVariable.value());
		}
		JobResult result = executor.execute(job, variables);
		if (result.status() == JobResultStatus.FAILED && job.allowFailure()) {
			return new JobResult(job.name(), JobResultStatus.ALLOWED_FAILURE, result.exitCode(), result.message());
		}
		return result;
	}

	private boolean eligible(
			JobDefinition job,
			Map<String, JobDefinition> jobs,
			Map<String, JobResult> results,
			PipelineDefinition pipeline) {
		if (!results.keySet().containsAll(job.needs())) {
			return false;
		}
		if (!job.needs().isEmpty()) {
			return true;
		}
		int stageIndex = pipeline.stages().indexOf(job.stage());
		return jobs.values().stream()
				.filter(candidate -> pipeline.stages().indexOf(candidate.stage()) < stageIndex)
				.allMatch(candidate -> results.containsKey(candidate.name()));
	}

	private boolean hasFailedDependency(JobDefinition job, Map<String, JobResult> results) {
		return job.needs().stream().map(results::get)
				.anyMatch(result -> result != null && result.status() == JobResultStatus.FAILED);
	}

	private Map<String, String> variables(List<YamlVariable> yamlVariables) {
		Map<String, String> values = new HashMap<>();
		for (YamlVariable yamlVariable : yamlVariables) {
			values.put(yamlVariable.name(), yamlVariable.value());
		}
		return values;
	}
}
