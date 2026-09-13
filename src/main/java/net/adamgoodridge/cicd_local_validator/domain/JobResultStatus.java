package net.adamgoodridge.cicd_local_validator.domain;

public enum JobResultStatus {
	PENDING,
	RUNNING,
	PASSED,
	FAILED,
	ALLOWED_FAILURE,
	BLOCKED,
	SKIPPED
}
