package net.adamgoodridge.cicd_local_validator.pipeline;

import net.adamgoodridge.cicd_local_validator.domain.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PipelineParser {
	private static final Set<String> RESERVED_KEYS = Set.of("stages", "variables", "yamlVariables");
	private static final Set<String> SUPPORTED_JOB_KEYS = Set.of(
			"image", "script", "stage", "tags", "depends_on", "needs", "variables", "yamlVariables", "artifacts", "allow_failure", "when", "extends");

	public PipelineParseResult parse(String source) {
		List<ValidationIssue> issues = new ArrayList<>();
		Object loaded;
		try {
			loaded = new Yaml().load(source);
		} catch (YAMLException exception) {
			return invalid("pipeline", "Invalid YAML: " + exception.getMessage());
		}

		if (!(loaded instanceof Map<?, ?> root)) {
			return invalid("pipeline", "The pipeline must be a YAML mapping.");
		}

		List<String> stages = readStringList(root.get("stages"), "stages", issues);
		if (stages.isEmpty()) {
			stages = List.of("build", "test", "deploy");
		}
		List<YamlVariable> pipelineYamlVariables = new ArrayList<>();
		pipelineYamlVariables.addAll(readVariables(root.get("variables"), "variables", issues));
		pipelineYamlVariables.addAll(readVariables(root.get("yamlVariables"), "yamlVariables", issues));
		Map<String, Map<?, ?>> jobMapsByName = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : root.entrySet()) {
			if (!(entry.getKey() instanceof String name) || RESERVED_KEYS.contains(name)) {
				continue;
			}
			if (!(entry.getValue() instanceof Map<?, ?> jobMap)) {
				issues.add(new ValidationIssue(name, "Job definition must be a YAML mapping."));
				continue;
			}
			jobMapsByName.put(name, jobMap);
		}

		List<JobDefinition> jobs = new ArrayList<>();
		for (Map.Entry<String, Map<?, ?>> entry : jobMapsByName.entrySet()) {
			String name = entry.getKey();
			if (name.startsWith(".")) {
				continue;
			}
			Map<Object, Object> jobMap = resolveJobMap(name, jobMapsByName, issues, new HashSet<>());
			for (Object key : jobMap.keySet()) {
				if (key instanceof String jobKey && !SUPPORTED_JOB_KEYS.contains(jobKey)) {
					issues.add(new ValidationIssue(name + "." + jobKey, "Unsupported Job property."));
				}
			}
			jobs.add(readJob(name, jobMap, issues));
		}

		return new PipelineParseResult(new PipelineDefinition(stages, pipelineYamlVariables, jobs), issues);
	}

	private Map<Object, Object> resolveJobMap(String name, Map<String, Map<?, ?>> jobMapsByName,
			List<ValidationIssue> issues, Set<String> resolving) {
		if (!resolving.add(name)) {
			issues.add(new ValidationIssue(name + ".extends", "Circular extends definition."));
			return Map.of();
		}
		Map<?, ?> jobMap = jobMapsByName.get(name);
		Map<Object, Object> resolved = new LinkedHashMap<>();
		for (String parentName : readStringList(jobMap.get("extends"), name + ".extends", issues)) {
			if (!jobMapsByName.containsKey(parentName)) {
				issues.add(new ValidationIssue(name + ".extends", "Unknown template: " + parentName));
				continue;
			}
			mergeInto(resolved, resolveJobMap(parentName, jobMapsByName, issues, resolving));
		}
		for (Map.Entry<?, ?> entry : jobMap.entrySet()) {
			if (!"extends".equals(entry.getKey())) {
				mergeValue(resolved, entry.getKey(), entry.getValue());
			}
		}
		resolving.remove(name);
		return resolved;
	}

	private void mergeInto(Map<Object, Object> target, Map<?, ?> source) {
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			mergeValue(target, entry.getKey(), entry.getValue());
		}
	}

	private void mergeValue(Map<Object, Object> target, Object key, Object value) {
		if (target.get(key) instanceof Map<?, ?> existing && value instanceof Map<?, ?> override) {
			Map<Object, Object> merged = new LinkedHashMap<>();
			mergeInto(merged, existing);
			mergeInto(merged, override);
			target.put(key, merged);
			return;
		}
		target.put(key, value);
	}

	private JobDefinition readJob(String name, Map<?, ?> values, List<ValidationIssue> issues) {
		String image = readString(values.get("image"), name + ".image", issues);
		List<String> script = readStringList(values.get("script"), name + ".script", issues);
		String stage = values.containsKey("stage")
				? readString(values.get("stage"), name + ".stage", issues)
				: "test";
		List<String> needs = readStringList(values.get("needs"), name + ".needs", issues);
		List<YamlVariable> yamlVariables = new ArrayList<>();
		yamlVariables.addAll(readVariables(values.get("variables"), name + ".variables", issues));
		yamlVariables.addAll(readVariables(values.get("yamlVariables"), name + ".yamlVariables", issues));
		Artifact artifacts = readArtifact(values.get("artifacts"), name + ".artifacts", issues);
		boolean allowFailure = readBoolean(values.get("allow_failure"), name + ".allow_failure", issues);
		boolean alwaysRun = "always".equals(readString(values.get("when"), name + ".when", issues, "on_success"));
		return new JobDefinition(name, image, script, stage, needs, yamlVariables, artifacts, allowFailure, alwaysRun);
	}

	private Artifact readArtifact(Object value, String path, List<ValidationIssue> issues) {
		if (value == null) {
			return null;
		}
		if (!(value instanceof Map<?, ?> artifactMap)) {
			issues.add(new ValidationIssue(path, "Artifacts must be a YAML mapping."));
			return null;
		}
		return new Artifact(readStringList(artifactMap.get("paths"), path + ".paths", issues));
	}

	private List<YamlVariable> readVariables(Object value, String path, List<ValidationIssue> issues) {
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof Map<?, ?> variableMap)) {
			issues.add(new ValidationIssue(path, "Variables must be a YAML mapping."));
			return List.of();
		}
		List<YamlVariable> yamlVariables = new ArrayList<>();
		for (Map.Entry<?, ?> entry : variableMap.entrySet()) {
			if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String variableValue)) {
				issues.add(new ValidationIssue(path, "YamlVariable names and values must be strings."));
				continue;
			}
			yamlVariables.add(new YamlVariable(name, variableValue));
		}
		return yamlVariables;
	}

	private List<String> readStringList(Object value, String path, List<ValidationIssue> issues) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof String stringValue) {
			return List.of(stringValue);
		}
		if (!(value instanceof Collection<?> collection)) {
			issues.add(new ValidationIssue(path, "Expected a string or list of strings."));
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (Object item : collection) {
			if (item instanceof String stringValue) {
				values.add(stringValue);
			} else {
				issues.add(new ValidationIssue(path, "List values must be strings."));
			}
		}
		return values;
	}


	private String readString(Object value, String path, List<ValidationIssue> issues) {
		return readString(value, path, issues, null);
	}

	private String readString(Object value, String path, List<ValidationIssue> issues, String defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof String stringValue) {
			return stringValue;
		}
		issues.add(new ValidationIssue(path, "Expected a string."));
		return defaultValue;
	}

	private boolean readBoolean(Object value, String path, List<ValidationIssue> issues) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}
		issues.add(new ValidationIssue(path, "Expected a boolean."));
		return false;
	}

	private PipelineParseResult invalid(String path, String message) {
		return new PipelineParseResult(
				new PipelineDefinition(List.of(), List.of(), List.of()),
				List.of(new ValidationIssue(path, message)));
	}
}
