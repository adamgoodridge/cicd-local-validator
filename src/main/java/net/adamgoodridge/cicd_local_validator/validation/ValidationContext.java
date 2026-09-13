package net.adamgoodridge.cicd_local_validator.validation;

import net.adamgoodridge.cicd_local_validator.domain.*;

import java.util.*;

public record ValidationContext(
		PipelineDefinition pipeline,
		Set<String> stageNames,
		Map<String, JobDefinition> jobs,
		List<ValidationIssue> issues) {
}