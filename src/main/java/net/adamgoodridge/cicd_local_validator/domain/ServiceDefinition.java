package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;

public record ServiceDefinition(
		String image,
		List<String> aliases,
		List<String> command,
		List<String> entrypoint) {
	public ServiceDefinition {
		aliases = List.copyOf(aliases);
		command = List.copyOf(command);
		entrypoint = List.copyOf(entrypoint);
	}
}