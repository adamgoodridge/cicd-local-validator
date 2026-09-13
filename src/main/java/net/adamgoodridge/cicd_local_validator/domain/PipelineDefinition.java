package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;

public record PipelineDefinition(
		List<String> stages,
		List<YamlVariable> yamlVariables,
		List<JobDefinition> jobs) {
	public PipelineDefinition {
		stages = List.copyOf(stages);
		yamlVariables = List.copyOf(yamlVariables);
		jobs = List.copyOf(jobs);
	}
}
