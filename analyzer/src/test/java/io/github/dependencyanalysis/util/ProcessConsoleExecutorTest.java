package io.github.dependencyanalysis.util;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests lossless live forwarding and separation of command return data. */
@Timeout(ProcessConsoleExecutorTest.TIMEOUT_SECONDS)
class ProcessConsoleExecutorTest {

    /** Maximum fixture execution duration. */
    static final int TIMEOUT_SECONDS = 30;

    /** Rendezvous wait in seconds. */
    private static final int WAIT_SECONDS = 10;

    /** Mixed fixture line count. */
    private static final int MIXED_LINES = 6;

    /** Output volume exceeding pipe capacity and former tail limits. */
    private static final int OUTPUT_LINES = 4000;

    /** Fixture rendezvous directory. */
    @TempDir
    private Path temporary;

    @ParameterizedTest
    @EnumSource(LogVerbosity.class)
    void forwardsEveryLineAtEveryVerbosity(final LogVerbosity verbosity)
            throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ProcessConsoleResult result = ProcessConsoleExecutor.execute(
                builder("lines"), log(bytes, verbosity), context());
        final String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.standardOutput()).isEmpty();
        assertThat(output).contains("[INFO] info", "[ERROR] error",
                "Caused by: 中文", "final-without-newline");
        assertThat(output.lines()).hasSize(MIXED_LINES);
        assertThat(output.lines())
                .allMatch(line -> line.startsWith("[process]"));
        assertThat(output).doesNotContain(
                "[DEBUG][process]", "[ERROR][process]");
    }

    @Test
    void drainsLargeConcurrentStreamsWithoutTruncation() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ProcessConsoleResult result = ProcessConsoleExecutor.execute(
                builder("large"), log(bytes, LogVerbosity.INFO), context());
        final String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(result.standardOutput()).isEmpty();
        assertThat(output.lines()).hasSize(OUTPUT_LINES * 2);
        for (int index = 0; index < OUTPUT_LINES; index++) {
            assertThat(output).containsOnlyOnce(" out-" + index + "-end")
                    .containsOnlyOnce(" err-" + index + "-end");
        }
    }

    @Test
    void keepsStdoutDataSeparateFromStderr() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ProcessConsoleResult result = ProcessConsoleExecutor.executeData(
                builder("lines"), log(bytes, LogVerbosity.INFO), context());
        assertThat(result.standardOutput()).contains("[INFO] info", "\r\n\r\n",
                "final-without-newline").doesNotContain("[ERROR] error");
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[ERROR] error", "Caused by: 中文")
                .doesNotContain("[INFO] info", "final-without-newline");
    }

    @Test
    void forwardsBeforeProcessExits() throws Exception {
        final Path release = temporary.resolve("release");
        final CompletableFuture<Void> observed = new CompletableFuture<>();
        final PrintStream output = new PrintStream(
                new ByteArrayOutputStream()) {
            @Override
            public void println(final String line) {
                if (line.endsWith(" ready")) {
                    observed.complete(null);
                }
            }
        };
        final CompletableFuture<ProcessConsoleResult> execution =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return ProcessConsoleExecutor.execute(
                                builder("wait", release.toString()),
                                new DiagnosticLog(output, LogVerbosity.INFO),
                                context());
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        try {
            observed.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(execution).isNotDone();
        } finally {
            Files.createFile(release);
        }
        assertThat(execution.get(WAIT_SECONDS, TimeUnit.SECONDS)
                .exitCode()).isZero();
    }

    @Test
    void reportsStartFailure() {
        assertThatThrownBy(() -> ProcessConsoleExecutor.execute(
                new ProcessBuilder(temporary.resolve("missing").toString()),
                log(new ByteArrayOutputStream(), LogVerbosity.INFO), context()))
                .isInstanceOf(java.io.IOException.class);
    }

    private DiagnosticContext context() {
        return DiagnosticContext.of("process", "fixture");
    }

    private DiagnosticLog log(final ByteArrayOutputStream bytes,
            final LogVerbosity verbosity) {
        return new DiagnosticLog(new PrintStream(bytes, true,
                StandardCharsets.UTF_8), verbosity);
    }

    private ProcessBuilder builder(final String... arguments) {
        final java.util.List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows")
                        ? "java.exe" : "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(OutputFixture.class.getName());
        command.addAll(java.util.List.of(arguments));
        return new ProcessBuilder(command);
    }

    /** Child process fixture. */
    public static final class OutputFixture {
        /** Private constructor. */
        private OutputFixture() {
        }

        /**
         * Emits output or waits for the parent to acknowledge live output.
         * @param arguments fixture mode and optional rendezvous path
         * @throws Exception on fixture failure
         */
        public static void main(final String[] arguments) throws Exception {
            if (arguments[0].equals("wait")) {
                System.out.println("ready");
                while (!Files.exists(Path.of(arguments[1]))) {
                    Thread.sleep(WAIT_SECONDS);
                }
            } else if (arguments[0].equals("large")) {
                final Thread error = new Thread(() -> {
                    for (int index = 0; index < OUTPUT_LINES; index++) {
                        System.err.println("err-" + index + "-end");
                    }
                });
                error.start();
                for (int index = 0; index < OUTPUT_LINES; index++) {
                    System.out.println("out-" + index + "-end");
                }
                error.join();
            } else {
                System.out.print("[INFO] info\r\n\r\nplain\n");
                System.err.println("[ERROR] error");
                System.err.println("Caused by: 中文");
                System.out.print("final-without-newline");
                System.exit(1);
            }
        }
    }
}
