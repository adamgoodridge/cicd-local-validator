package net.adamgoodridge.cicd_local_validator.constants;

public final class ScriptConstants {
	private ScriptConstants() {
		throw new IllegalStateException("Utility class");
	}

	public static final String SINGLE_LINE_OUTPUT_PREFIX = ">";
	public static final String YAML_MULTILINE_BLOCK = "|";
	public static final String MULTILINE_OUTPUT_PREFIX = "|>";
}
