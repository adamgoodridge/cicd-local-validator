package net.adamgoodridge.cicd_local_validator.validation;

import net.adamgoodridge.cicd_local_validator.pipeline.PipelineParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineValidatorTest {
	private final PipelineValidator validator = new PipelineValidator(new PipelineParser());

	@Test
	void reportsMissingJobFieldsAndUnknownDependencies() {
		var result = validator.validate("""
				stages: [test]
				unit:
				  stage: test
				  needs: [compile]
				""");

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).extracting("path")
				.contains("unit.image", "unit.script", "unit.needs");
	}

	@Test
	void acceptsGitlabServices() {
		var result = validator.validate("""
				stages: [test]
				unit:
				  image: alpine:3.20
				  script: echo test
				  services: [docker:dind]
				""");

		assertThat(result.isValid()).isTrue();
	}
}
