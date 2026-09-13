package net.adamgoodridge.cicd_local_validator.facade;

/**
 * Custom exception thrown when GitLab API operations fail.
 * Wraps underlying GitLab API errors with contextual information.
 */
public class GitLabApiException extends RuntimeException {

	public GitLabApiException(String message) {
		super(message);
	}

	public GitLabApiException(String message, Throwable cause) {
		super(message, cause);
	}
}

