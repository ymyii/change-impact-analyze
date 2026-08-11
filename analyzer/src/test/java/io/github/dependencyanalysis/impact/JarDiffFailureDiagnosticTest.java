package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.BytecodeDiffException;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests isolated JAR diff failure Console and report diagnostics. */
class JarDiffFailureDiagnosticTest {

    /** JAR pair context. */
    private static final DiagnosticContext CONTEXT = DiagnosticContext.of(
            "jar-diff", "pair").withArtifact("example:library:1->2");

    @Test
    void infoEmitsCompleteMessageWithoutStackTrace() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog diagnostics = log(bytes, LogVerbosity.INFO);
        final BytecodeDiffException failure = failure();

        final String summary = JarDiffFailureDiagnostic.emit(
                diagnostics, CONTEXT, failure);

        assertThat(summary)
                .startsWith("BytecodeDiffException: Bytecode diff error")
                .contains("jar=broken.jar")
                .contains("class=example/Api.class")
                .contains("message=Failed to hash method");
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[WARN][jar-diff][pair]")
                .contains("JAR comparison failed: " + summary)
                .doesNotContain("\tat ")
                .doesNotContain("Caused by:");
        assertThat(diagnostics.getEvents()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(
                            DiagnosticLevel.WARN);
                    assertThat(event.getMessage()).isEqualTo(
                            "JAR comparison failed: " + summary);
                });
    }

    @Test
    void debugEmitsCompleteStackAndCauseAsTransientLines() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog diagnostics = log(bytes, LogVerbosity.DEBUG);
        final BytecodeDiffException failure = failure();

        final String summary = JarDiffFailureDiagnostic.emit(
                diagnostics, CONTEXT, failure);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("JAR comparison failed: " + summary)
                .contains("[DEBUG][jar-diff][pair]")
                .contains("BytecodeDiffException: Bytecode diff error")
                .contains("Caused by: java.lang.IllegalStateException: "
                        + "invalid opcode");
        assertThat(diagnostics.getEvents()).singleElement()
                .satisfies(event -> assertThat(event.getLevel())
                        .isEqualTo(DiagnosticLevel.WARN));
    }

    private DiagnosticLog log(
            final ByteArrayOutputStream bytes,
            final LogVerbosity verbosity) {
        return new DiagnosticLog(new PrintStream(
                bytes, true, StandardCharsets.UTF_8), verbosity);
    }

    private BytecodeDiffException failure() {
        return new BytecodeDiffException(
                Path.of("broken.jar"), "example/Api.class",
                "Failed to hash method",
                new IllegalStateException("invalid opcode"));
    }
}
