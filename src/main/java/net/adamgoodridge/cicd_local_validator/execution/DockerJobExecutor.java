package net.adamgoodridge.cicd_local_validator.execution;

import net.adamgoodridge.cicd_local_validator.domain.JobDefinition;
import net.adamgoodridge.cicd_local_validator.domain.JobResult;

import java.util.Map;

public interface DockerJobExecutor {
	JobResult execute(JobDefinition job, Map<String, String> variables);
}
