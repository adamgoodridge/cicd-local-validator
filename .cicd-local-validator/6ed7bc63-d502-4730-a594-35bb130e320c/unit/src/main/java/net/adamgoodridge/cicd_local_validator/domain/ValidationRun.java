package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;
import java.util.UUID;

public record ValidationRun(UUID id, List<JobResult> jobResults, PipelineResultStatus pipelineStatus) {
	public ValidationRun {
		jobResults = List.copyOf(jobResults);
	}
}
