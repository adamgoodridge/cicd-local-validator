package net.adamgoodridge.cicd_local_validator.constants;


import jakarta.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

@Component
public class GitlabConstants {
	@Value("${gitlab.api.url}")
	private String gitlabApiUrl;
	@Value("${gitlab.api.token}")
	private String gitlabApiToken;

	private static GitlabConstants instance;
	@PostConstruct
	@SuppressWarnings("unused")
	public void init() {
		instance = this;
	}
	public static GitlabConstants getInstance() {
		return instance;
	}

	public String getGitlabApiUrl() {
		return gitlabApiUrl;
	}

	public String getGitlabApiToken() {
		return gitlabApiToken;
	}
}
