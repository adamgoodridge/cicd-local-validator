package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;

public record JobDefinition(
		String name,
		String image,
		List<String> script,
		String stage,
		List<String> needs,
		List<YamlVariable> yamlVariables,
		Artifact artifacts,
		boolean allowFailure,
		boolean alwaysRun) {
	public JobDefinition {
		script = List.copyOf(script);
		needs = List.copyOf(needs);
		yamlVariables = List.copyOf(yamlVariables);
	}
	public boolean isNameBlank() {
		return name == null || name.isBlank();
	}
	public boolean isImageBlank() {
		return image == null || image.isBlank();
	}
}
