package net.adamgoodridge.cicd_local_validator.domain;

import java.util.List;

public record Artifact(List<String> paths) {
	public Artifact {
		paths = List.copyOf(paths);
	}
}
