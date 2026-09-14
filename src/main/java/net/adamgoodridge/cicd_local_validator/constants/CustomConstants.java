package net.adamgoodridge.cicd_local_validator.constants;

import jakarta.annotation.*;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.*;

@Component
public class CustomConstants {
	public String localWorkspace = "/workspace";
	@Value("${custom.docker.command:docker}")
	public String dockerCommand;
	@Getter
	private static CustomConstants instance;
	@PostConstruct
	@SuppressWarnings("unused")
	public void init() {
		instance = this;
	}

}
