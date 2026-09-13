package net.adamgoodridge.cicd_local_validator.pipeline;

import net.adamgoodridge.cicd_local_validator.domain.PipelineDefinition;
import net.adamgoodridge.cicd_local_validator.domain.ValidationIssue;

import java.util.List;

public record PipelineParseResult(PipelineDefinition pipeline, List<ValidationIssue> issues) {
	public PipelineParseResult {
		issues = List.copyOf(issues);
	}

	public boolean isValid() {
		return issues.isEmpty();
	}
}
