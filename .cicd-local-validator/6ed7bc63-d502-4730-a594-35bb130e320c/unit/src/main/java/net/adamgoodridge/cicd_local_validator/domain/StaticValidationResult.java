package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;

public record StaticValidationResult(List<ValidationIssue> issues) {
	public StaticValidationResult {
		issues = List.copyOf(issues);
	}

	public boolean isValid() {
		return issues.isEmpty();
	}
}
