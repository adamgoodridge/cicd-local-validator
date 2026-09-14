package net.adamgoodridge.cicd_local_validator;

public final class ErrorMessages {
	private ErrorMessages() {
		throw new IllegalStateException("Utility class");
	}

	public static final String JOB_NAME_BLANK = "Job name is blank";
	public static final String JOB_IMAGE_BLANK = "Job image is blank";
	public static final String JOB_SCRIPT_BLANK = "Job script is blank";
	public static final String JOB_STAGE_NOT_FOUND = "Job stage not found in pipeline stages";
	public static final String JOB_NEEDS_NOT_FOUND = "Job needs not found in pipeline jobs";
	public static final String JOB_TIMED_OUT = "Job timed out after ";
	public static final String JOB_UNABLE_START_LOCAL = "Unable to start local Job: ";
	public static final String JOB_COULD_NOT_BE_SCHEDULED = "Job could not be scheduled.";
	public static final String JOB_REQUIRED_DEPENDENCY_FAILED = "A required Dependency failed.";
}
