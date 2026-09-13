package net.adamgoodridge.cicd_local_validator.execution;

import net.adamgoodridge.cicd_local_validator.constants.CustomConstants;
import net.adamgoodridge.cicd_local_validator.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalJobExecutorTest {
	@TempDir
	Path workingDirectory;

	@Test
	void usesCustomDockerCommandWhenConfigured() {
		CustomConstants original = CustomConstants.getInstance();
		CustomConstants configured = new CustomConstants();
		configured.dockerCommand = "podman";
		configured.init();

		try {
			List<String> capturedCommand = new ArrayList<>();
			var executor = new LocalJobExecutor(workingDirectory) {
				@Override
				protected Process startProcess(List<String> command) {
					capturedCommand.clear();
					capturedCommand.addAll(command);
					return new Process() {
						@Override
						public OutputStream getOutputStream() {
							return OutputStream.nullOutputStream();
						}

						@Override
						public InputStream getInputStream() {
							return new ByteArrayInputStream("ok".getBytes());
						}

						@Override
						public InputStream getErrorStream() {
							return new ByteArrayInputStream(new byte[0]);
						}

						@Override
						public int waitFor() {
							return 0;
						}

						@Override
						public boolean waitFor(long timeout, TimeUnit unit) {
							return true;
						}

						@Override
						public int exitValue() {
							return 0;
						}

						@Override
						public void destroy() {
							throw new UnsupportedOperationException("destroy is not used by this test process");
						}
					};
				}
			};

			var result = executor.execute(job("echo custom"), Map.of());

			assertThat(result.status()).isEqualTo(JobResultStatus.PASSED);
			assertThat(capturedCommand).isNotEmpty();
			assertThat(capturedCommand.getFirst()).isEqualTo("podman");
		} finally {
			if (original != null) {
				original.init();
			}
		}
	}

	@Test
	void runsScriptInWorkingDirectoryWithVariables() {
		var job = job("printf '%s' \"$MODE\" > result.txt");
		var executor = new LocalJobExecutor(workingDirectory);

		var result = executor.execute(job, Map.of("MODE", "local"));

		assertThat(result.status()).isEqualTo(JobResultStatus.PASSED);
		assertThat(result.exitCode()).isZero();
		assertThat(result.message()).contains("> printf '%s' \"$MODE\" > result.txt");
		assertThat(workingDirectory.resolve("result.txt")).hasContent("local");
	}

	@Test
	void recordsNonZeroExitAsFailed() {
		var result = new LocalJobExecutor(workingDirectory)
				.execute(job("printf 'bad output'; exit 7"), Map.of());

		assertThat(result.status()).isEqualTo(JobResultStatus.FAILED);
		assertThat(result.exitCode()).isEqualTo(7);
		assertThat(result.message()).contains("> printf 'bad output'; exit 7");
		assertThat(result.message()).contains("bad output");
	}

	@Test
	void prefixesEachScriptLineInOutput() {
		var job = new JobDefinition("local-job", "alpine", List.of("echo first", "echo second"), "test", List.of(),
				List.of(new YamlVariable("JOB_KIND", "local")), null, false, false);

		var result = new LocalJobExecutor(workingDirectory).execute(job, Map.of());

		assertThat(result.status()).isEqualTo(JobResultStatus.PASSED);
		assertThat(result.message()).contains("> echo first");
		assertThat(result.message()).contains("first");
		assertThat(result.message()).contains("> echo second");
		assertThat(result.message()).contains("second");
	}

	@Test
	void prefixesEachLineFromMultilineScriptEntry() {
		var job = new JobDefinition("local-job", "alpine", List.of("echo first\necho second"), "test", List.of(),
				List.of(new YamlVariable("JOB_KIND", "local")), null, false, false);

		var result = new LocalJobExecutor(workingDirectory).execute(job, Map.of());

		assertThat(result.status()).isEqualTo(JobResultStatus.PASSED);
		assertThat(result.message()).contains("|> echo first");
		assertThat(result.message()).contains("|> echo second");
	}

	@Test
	void recordsTimeoutAsFailed() {
		var executor = new LocalJobExecutor(workingDirectory, 20, TimeUnit.MILLISECONDS);

		var result = executor.execute(job("sleep 1"), Map.of());

		assertThat(result.status()).isEqualTo(JobResultStatus.FAILED);
		assertThat(result.exitCode()).isNull();
		assertThat(result.message()).contains("timed out");
	}

	@Test
	void recordsOutputReadFailureMessage() {
		var executor = new LocalJobExecutor(workingDirectory) {
			@Override
			protected Process startProcess(List<String> command) {
				return new Process() {
					@Override
					public OutputStream getOutputStream() {
						return OutputStream.nullOutputStream();
					}

					@Override
					public InputStream getInputStream() {
						return new InputStream() {
							@Override
							public int read() throws IOException {
								throw new IOException("simulated read error");
							}
						};
					}

					@Override
					public InputStream getErrorStream() {
						return new ByteArrayInputStream(new byte[0]);
					}

					@Override
					public int waitFor() {
						return 0;
					}

					@Override
					public boolean waitFor(long timeout, TimeUnit unit) {
						return true;
					}

					@Override
					public int exitValue() {
						return 0;
					}

					@Override
					public void destroy() {
						throw new UnsupportedOperationException("destroy is not used by this test process");
					}
				};
			}
		};

		var result = executor.execute(job("echo local"), Map.of());

		assertThat(result.status()).isEqualTo(JobResultStatus.PASSED);
		assertThat(result.exitCode()).isZero();
		assertThat(result.message()).contains("Unable to read local Job output: simulated read error");
	}

	private JobDefinition job(String script) {
		return new JobDefinition("local-job", "alpine", List.of(script), "test", List.of(),
				List.of(new YamlVariable("JOB_KIND", "local")), null, false, false);
	}
}
