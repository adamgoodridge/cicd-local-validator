package net.adamgoodridge.cicd_local_validator.facade;

import net.adamgoodridge.cicd_local_validator.constants.GitlabConstants;
import net.adamgoodridge.cicd_local_validator.domain.*;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitLabFacade {

	/**
	 * Retrieves all yamlVariables from GitLab for the configured project.
	 *
	 * @param projectId the GitLab project ID
	 * @return a list of YamlVariable objects containing all yamlVariables from GitLab
	 */
	public List<Variable> getAllVariables(String projectId) {
		GitlabConstants constants = GitlabConstants.getInstance();
		GitLabApi gitLabApi = new GitLabApi(constants.getGitlabApiUrl(), constants.getGitlabApiToken());

		try {
			List<org.gitlab4j.api.models.Variable> gitLabVariables =
				gitLabApi.getProjectApi().getVariables(projectId);

			return gitLabVariables.stream().toList();
		} catch (Exception e) {
			throw new GitLabApiException("Failed to retrieve yamlVariables from GitLab for project: " + projectId, e);
		}
	}

	/**
	 * Retrieves all yamlVariables from GitLab for the configured project (by numeric ID).
	 *
	 * @param projectId the GitLab project ID (numeric)
	 * @return a list of YamlVariable objects containing all yamlVariables from GitLab
	 */
	public List<Variable> getAllVariables(int projectId) {
		return getAllVariables(String.valueOf(projectId));
	}
}

