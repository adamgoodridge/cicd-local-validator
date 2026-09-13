package net.adamgoodridge.cicd_local_validator.domain;

public record JobResult(
		String jobName,
		JobResultStatus status,
		Integer exitCode,
		String message) {
}
