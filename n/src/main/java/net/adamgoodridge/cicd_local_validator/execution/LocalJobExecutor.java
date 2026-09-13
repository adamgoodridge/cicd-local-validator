package net.adamgoodridge.cicd_local_validator.execution;

import lombok.*;
import net.adamgoodridge.cicd_local_validator.*;
import net.adamgoodridge.cicd_local_validator.constants.*;
import net.adamgoodridge.cicd_local_validator.domain.JobDefinition;
import net.adamgoodridge.cicd_local_validator.domain.JobResult;
import net.adamgoodridge.cicd_local_validator.domain.JobResultStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class LocalJobExecutor implements DockerJobExecutor {
	private static final String DEFAULT_DOCKER_COMMAND = "docker";

	private final Path workingDirectory;
	private final long timeout;
	private final TimeUnit timeoutUnit;

	public LocalJobExecutor(Path workingDirectory) {
		this(workingDirectory, 10, TimeUnit.MINUTES);
	}

	@Override
	public JobResult execute(JobDefinition job, Map<String, String> variables) {
		try {
			Files.createDirectories(workingDirectory);
			List<String> command = new ArrayList<>(List.of(
					resolveDockerCommand(), "run", "--rm",
					"--volume", workingDirectory.toAbsolutePath() + ":/workspace",
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
			return new JobResult(job.name(),
					exitCode == 0 ? JobResultStatus.PASSED : JobResultStatus.FAILED,
					exitCode, output.join());
		} catch (IOException exception) {
			return new JobResult(job.name(), JobResultStatus.FAILED, null,
					ErrorMessages.JOB_UNABLE_START_LOCAL + exception.getMessage());
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			return new JobResult(job.name(), JobResultStatus.FAILED, null,
					"Local Job execution was interrupted.");
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
