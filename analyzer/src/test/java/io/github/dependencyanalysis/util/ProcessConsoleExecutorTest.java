package io.github.dependencyanalysis.util;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests process output streaming and verbosity. */
class ProcessConsoleExecutorTest {

    /** Expected fixture output line count. */
    private static final int OUTPUT_LINES = 4;

    @Test
    void infoShowsOnlyWarningsAndErrors() throws Exception {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(
                bytes, true, StandardCharsets.UTF_8);
        final DiagnosticLog diagnostics =
                new DiagnosticLog(stream, LogVerbosity.INFO);

        final ProcessConsoleResult result = execute(diagnostics);

        assertThat(result.exitCode()).isZero();
        assertThat(result.outputTail().lines())
                .hasSize(OUTPUT_LINES);
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("][WARN][process][fixture][-] [WARNING] warning")
                .contains("][ERROR][process][fixture][-] [ERROR] error")
                .doesNotContain("[INFO] info", "plain");
        assertThat(diagnostics.getEvents()).isEmpty();
    }

    @Test
    void debugShowsCompleteProcessOutput() throws Exception {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(
                bytes, true, StandardCharsets.UTF_8);
        final DiagnosticLog diagnostics =
                new DiagnosticLog(stream, LogVerbosity.DEBUG);

        execute(diagnostics);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("][INFO][process][fixture][-] [INFO] info")
                .contains("][WARN][process][fixture][-] [WARNING] warning")
                .contains("][ERROR][process][fixture][-] [ERROR] error")
                .contains("][DEBUG][process][fixture][-] plain");
        assertThat(diagnostics.getEvents()).isEmpty();
    }

    private ProcessConsoleResult execute(
            final DiagnosticLog diagnostics)
            throws Exception {
        final String executable = Path.of(
                System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java")
                .toString();
        final ProcessBuilder builder = new ProcessBuilder(
                executable, "-cp",
                System.getProperty("java.class.path"),
                OutputFixture.class.getName());
        return ProcessConsoleExecutor.execute(
                builder, diagnostics,
                DiagnosticContext.of("process", "fixture"),
                OUTPUT_LINES);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    /** Process output fixture. */
    public static final class OutputFixture {

        /** Private constructor. */
        private OutputFixture() {
        }

        /**
         * Emits representative Maven output.
         *
         * @param arguments unused arguments
         */
        public static void main(final String[] arguments) {
            System.out.println("[INFO] info");
            System.out.println("[WARNING] warning");
            System.err.println("[ERROR] error");
            System.out.println("plain");
        }
    }
}
