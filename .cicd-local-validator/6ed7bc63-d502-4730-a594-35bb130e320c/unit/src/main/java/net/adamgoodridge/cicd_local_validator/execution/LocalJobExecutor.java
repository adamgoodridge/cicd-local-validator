package net.adamgoodridge.cicd_local_validator.execution;

import net.adamgoodridge.cicd_local_validator.*;
import net.adamgoodridge.cicd_local_validator.constants.*;
import net.adamgoodridge.cicd_local_validator.domain.JobDefinition;
import net.adamgoodridge.cicd_local_validator.domain.JobResult;
import net.adamgoodridge.cicd_local_validator.domain.JobResultStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LocalJobExecutor implements DockerJobExecutor {
	private static final String DEFAULT_DOCKER_COMMAND = "docker";

	private final Path sourceWorkspace;
	private final Path executionWorkspace;
	private final long timeout;
	private final TimeUnit timeoutUnit;
	private final Map<String, JobDefinition> completedJobs = new HashMap<>();
	private final Map<String, Path> jobWorkspaces = new HashMap<>();

	public LocalJobExecutor(Path workingDirectory) {
		this(workingDirectory, workingDirectory, 10, TimeUnit.MINUTES);
	}

	public LocalJobExecutor(Path workingDirectory, long timeout, TimeUnit timeoutUnit) {
		this(workingDirectory, workingDirectory, timeout, timeoutUnit);
	}

	public LocalJobExecutor(Path sourceWorkspace, Path executionWorkspace) {
		this(sourceWorkspace, executionWorkspace, 10, TimeUnit.MINUTES);
	}

	public LocalJobExecutor(Path sourceWorkspace, Path executionWorkspace, long timeout, TimeUnit timeoutUnit) {
		this.sourceWorkspace = sourceWorkspace;
		this.executionWorkspace = executionWorkspace;
		this.timeout = timeout;
		this.timeoutUnit = timeoutUnit;
	}

	@Override
	public JobResult execute(JobDefinition job, Map<String, String> variables) {
		try {
			Path jobWorkspace = prepareWorkspace(job);
			List<String> command = new ArrayList<>(List.of(
					resolveDockerCommand(), "run", "--rm",
					"--volume", jobWorkspace.toAbsolutePath() + ":/workspace",
					"--workdir", "/workspace"));
			for (Map.Entry<String, String> entry : variables.entrySet()) {
				command.add("--env");
				command.add(entry.getKey() + "=" + entry.getValue());
			}
			command.add(job.image());
			command.addAll(List.of("/bin/sh", "-c", instrumentedScript(job.script())));
			Process process = startProcess(command);
			CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(process.getInputStream().readAllBytes());
				} catch (IOException exception) {
					return "Unable to read local Job output: " + exception.getMessage();
				}
			});
			boolean completed = process.waitFor(timeout, timeoutUnit);
			if (!completed) {
				process.destroyForcibly();
				return new JobResult(job.name(), JobResultStatus.FAILED, null,
						ErrorMessages.JOB_TIMED_OUT + timeout + " " + timeoutUnit.toString().toLowerCase() + ".\n" + output.join());
			}
			int exitCode = process.exitValue();
			JobResult result = new JobResult(job.name(),
					exitCode == 0 ? JobResultStatus.PASSED : JobResultStatus.FAILED,
					exitCode, output.join());
			completedJobs.put(job.name(), job);
			jobWorkspaces.put(job.name(), jobWorkspace);
			return result;
		} catch (IOException exception) {
			return new JobResult(job.name(), JobResultStatus.FAILED, null,
					ErrorMessages.JOB_UNABLE_START_LOCAL + exception.getMessage());
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			return new JobResult(job.name(), JobResultStatus.FAILED, null,
					"Local Job execution was interrupted.");
		}
	}

	private Path prepareWorkspace(JobDefinition job) throws IOException {
		if (sourceWorkspace.equals(executionWorkspace)) {
			Files.createDirectories(sourceWorkspace);
			return sourceWorkspace;
		}
		Path jobWorkspace = executionWorkspace.resolve(job.name()).normalize();
		if (!jobWorkspace.startsWith(executionWorkspace)) {
			throw new IOException("Invalid Job name for workspace: " + job.name());
		}
		Files.createDirectories(jobWorkspace);
		copyWorkspace(sourceWorkspace, jobWorkspace);
		for (String dependencyName : job.needs()) {
			JobDefinition dependency = completedJobs.get(dependencyName);
			Path dependencyWorkspace = jobWorkspaces.get(dependencyName);
			if (dependency != null && dependencyWorkspace != null && dependency.artifacts() != null) {
				copyArtifacts(dependencyWorkspace, jobWorkspace, dependency.artifacts().paths());
			}
		}
		return jobWorkspace;
	}

	private void copyWorkspace(Path source, Path target) throws IOException {
		try (var paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				if (path.startsWith(executionWorkspace)) {
					continue;
				}
				Path destination = target.resolve(source.relativize(path));
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private void copyArtifacts(Path source, Path target, List<String> artifactPaths) throws IOException {
		for (String artifactPath : artifactPaths) {
			Path artifact = source.resolve(artifactPath).normalize();
			if (!artifact.startsWith(source) || !Files.exists(artifact)) {
				continue;
			}
			try (var paths = Files.walk(artifact)) {
				for (Path path : paths.toList()) {
					Path destination = target.resolve(source.relativize(path));
					if (Files.isDirectory(path)) {
						Files.createDirectories(destination);
					} else {
						Files.createDirectories(destination.getParent());
						Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		}
	}

	protected Process startProcess(List<String> command) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		return processBuilder.start();
	}

	private String instrumentedScript(List<String> script) {
		StringBuilder builder = new StringBuilder();
		for (InstrumentedScriptLine line : normalizeScriptLines(script)) {
			builder.append("printf '%s\\n' ")
					.append(shellQuote(line.prefix() + " " + line.command()))
					.append("\n")
					.append(line.command())
					.append("\n");
		}
		return builder.toString();
	}

	private List<InstrumentedScriptLine> normalizeScriptLines(List<String> script) {
		List<InstrumentedScriptLine> normalized = new ArrayList<>();
		for (String entry : script) {
			String prefix = isMultilineEntry(entry)
					? ScriptConstants.MULTILINE_OUTPUT_PREFIX
					: ScriptConstants.SINGLE_LINE_OUTPUT_PREFIX;
			for (String line : entry.split("\\R", -1)) {
				if (!line.isBlank()) {
					normalized.add(new InstrumentedScriptLine(prefix, line));
				}
			}
		}
		return normalized;
	}

	private boolean isMultilineEntry(String entry) {
		return entry.contains("\n") || entry.contains("\r");
	}

	private record InstrumentedScriptLine(String prefix, String command) {
	}

	private String shellQuote(String value) {
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}

	private String resolveDockerCommand() {
		CustomConstants constants = CustomConstants.getInstance();
		if (constants == null || constants.dockerCommand == null || constants.dockerCommand.isBlank()) {
			return DEFAULT_DOCKER_COMMAND;
		}
		return constants.dockerCommand;
	}
}
