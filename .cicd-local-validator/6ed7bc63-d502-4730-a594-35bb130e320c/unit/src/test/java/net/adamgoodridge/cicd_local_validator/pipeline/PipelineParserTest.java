package net.adamgoodridge.cicd_local_validator.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineParserTest {
	private final PipelineParser parser = new PipelineParser();

	@Test
	void parsesSupportedPipelineProperties() {
		PipelineParseResult result = parser.parse(yaml("pipelines/valid/supported-properties.yml"));

		assertThat(result.isValid()).isTrue();
		assertThat(result.pipeline().jobs()).hasSize(2);
		assertThat(result.pipeline().jobs().get(1).alwaysRun()).isTrue();
		assertThat(result.pipeline().jobs().getFirst().image()).isEqualTo("alpine:3.20");
		assertThat(result.pipeline().jobs().getFirst().artifacts().paths()).containsExactly("build/output.txt");
	}

	@Test
	void parsesStandardGitlabVariables() {
		PipelineParseResult result = parser.parse(yaml("pipelines/valid/standard-variables.yml"));

		assertThat(result.isValid()).isTrue();
		assertThat(result.pipeline().yamlVariables()).hasSize(1);
		assertThat(result.pipeline().yamlVariables().getFirst().name()).isEqualTo("APP_MODE");
		assertThat(result.pipeline().jobs().getFirst().yamlVariables()).hasSize(1);
	}

	@Test
	void reportsMalformedYaml() {
		PipelineParseResult result = parser.parse(yaml("pipelines/invalid/malformed.yml"));

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue ->
				assertThat(issue.message()).startsWith("Invalid YAML:"));
	}

	@Test
	void preservesMultilineScriptBlockEntry() {
		PipelineParseResult result = parser.parse(yaml("pipelines/valid/multiline-script-block.yml"));

		assertThat(result.isValid()).isTrue();
		assertThat(result.pipeline().jobs()).hasSize(1);
		assertThat(result.pipeline().jobs().getFirst().script()).hasSize(1);
		assertThat(result.pipeline().jobs().getFirst().script().getFirst()).contains("echo first").contains("echo second");
	}

	private String yaml(String resourcePath) {
		try {
			return new String(Objects.requireNonNull(
					getClass().getClassLoader().getResourceAsStream(resourcePath),
					"Missing resource: " + resourcePath).readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read resource: " + resourcePath, exception);
		}
	}
}
