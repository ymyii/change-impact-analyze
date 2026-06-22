package io.github.changeimpact.analyze.cli;

import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for --build-java-home CLI option.
 */
class ChangeImpactAnalyzeCliBuildJavaHomeTest {

    @Test
    void buildJavaHomeOptionIsAccepted(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        final File fakeJavaHome =
                new File("/tmp/fake-java");
        final int code =
                ChangeImpactAnalyzeCli
                        .newCommandLine(cli)
                        .execute(
                                "--project",
                                temp.toString(),
                                "--baseline", "main",
                                "--output",
                                temp.resolve("out.html")
                                        .toString(),
                                "--build-java-home",
                                fakeJavaHome
                                        .getAbsolutePath());
        assertThat(code).isNotZero();
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        "pipeline".equals(
                                e.getStage()));
    }

    @Test
    void buildJavaHomeOptionAppearsInHelp() {
        final int code =
                ChangeImpactAnalyzeCli
                        .newCommandLine(
                                new ChangeImpactAnalyzeCli())
                        .execute("--help");
        assertThat(code).isZero();
    }
}
