package io.github.changeimpact.analyze.cli;

import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import io.github.changeimpact.analyze.diagnostic.DiagnosticLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChangeImpactAnalyzeCli}.
 */
class ChangeImpactAnalyzeCliTest {

    @Test
    void helpOptionReturnsZero() {
        final int code =
                ChangeImpactAnalyzeCli.newCommandLine(
                        new ChangeImpactAnalyzeCli())
                        .execute("--help");
        assertThat(code).isZero();
    }

    @Test
    void noArgsReturnsNonZero() {
        final int code =
                ChangeImpactAnalyzeCli.newCommandLine(
                        new ChangeImpactAnalyzeCli())
                        .execute();
        assertThat(code).isNotZero();
    }

    @Test
    void missingProjectReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--baseline", "main",
                "--output",
                temp.resolve("out.html").toString());
        assertThat(code).isNotZero();
    }

    @Test
    void missingBaselineReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--project", temp.toString(),
                "--output",
                temp.resolve("out.html").toString());
        assertThat(code).isNotZero();
    }

    @Test
    void missingOutputReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--project", temp.toString(),
                "--baseline", "main");
        assertThat(code).isNotZero();
    }

    @Test
    void invalidFormatReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--project", temp.toString(),
                "--baseline", "main",
                "--output",
                temp.resolve("out.xml").toString(),
                "--format", "xml");
        assertThat(code).isNotZero();
    }

    @Test
    void projectNotExistsReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--project",
                temp.resolve("nope").toString(),
                "--baseline", "main",
                "--output",
                temp.resolve("out.html").toString());
        assertThat(code).isNotZero();
    }

    @Test
    void projectNotDirectoryReturnsNonZero(
            @TempDir final Path temp)
            throws IOException {
        final Path file =
                temp.resolve("afile.txt");
        Files.writeString(file, "x");
        final int code = runCli(temp,
                "--project", file.toString(),
                "--baseline", "main",
                "--output",
                temp.resolve("out.html").toString());
        assertThat(code).isNotZero();
    }

    @Test
    void outputParentNotExistsReturnsNonZero(
            @TempDir final Path temp) {
        final int code = runCli(temp,
                "--project", temp.toString(),
                "--baseline", "main",
                "--output",
                temp.resolve("no/deep/out.html")
                        .toString());
        assertThat(code).isNotZero();
    }

    @Test
    void validParamsTriggersPipeline(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        final int code =
                ChangeImpactAnalyzeCli.newCommandLine(cli)
                        .execute(
                                "--project",
                                temp.toString(),
                                "--baseline", "main",
                                "--output",
                                temp.resolve("out.html")
                                        .toString());
        assertThat(code).isNotZero();
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        "pipeline".equals(
                                e.getStage())
                                && e.getLevel()
                                        == DiagnosticLevel.ERROR);
    }

    @Test
    void defaultFormatIsHtml(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", temp.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString());
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        e.getLevel()
                                == DiagnosticLevel.INFO);
    }

    @Test
    void validParamsEmitValidationStages(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", temp.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString());
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        "validation".equals(
                                e.getStage())
                        && e.getMessage().contains(
                                "started"))
                .anyMatch(e ->
                        "validation".equals(
                                e.getStage())
                        && e.getMessage().contains(
                                "ended"));
    }

    @Test
    void lowercaseFormatIsAccepted(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", temp.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString(),
                        "--format", "html");
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        "pipeline".equals(
                                e.getStage()));
    }

    @Test
    void mixedCaseFormatIsAccepted(
            @TempDir final Path temp) {
        final DiagnosticCollector collector =
                new DiagnosticCollector();
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", temp.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString(),
                        "--format", "Html");
        assertThat(collector.getEvents())
                .anyMatch(e ->
                        "pipeline".equals(
                                e.getStage()));
    }

    @Test
    void diagnosticErrorPrintedToStderr(
            @TempDir final Path temp)
            throws IOException {
        final Path file =
                temp.resolve("afile.txt");
        Files.writeString(file, "x");
        final ByteArrayOutputStream errBuf =
                new ByteArrayOutputStream();
        final DiagnosticCollector collector =
                new DiagnosticCollector(
                        new PrintStream(
                                new ByteArrayOutputStream()),
                        new PrintStream(errBuf));
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", file.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString());
        assertThat(errBuf.toString()).contains(
                "not a directory");
    }

    @Test
    void pipelineFailurePrintsToStderr(
            @TempDir final Path temp) {
        final ByteArrayOutputStream errBuf =
                new ByteArrayOutputStream();
        final DiagnosticCollector collector =
                new DiagnosticCollector(
                        new PrintStream(
                                new ByteArrayOutputStream()),
                        new PrintStream(errBuf));
        final ChangeImpactAnalyzeCli cli =
                new ChangeImpactAnalyzeCli(collector);
        ChangeImpactAnalyzeCli.newCommandLine(cli)
                .execute(
                        "--project", temp.toString(),
                        "--baseline", "main",
                        "--output",
                        temp.resolve("out.html")
                                .toString());
        assertThat(errBuf.toString()).contains(
                "Pipeline failed");
    }

    private int runCli(final Path temp,
                       final String... args) {
        return ChangeImpactAnalyzeCli.newCommandLine(
                new ChangeImpactAnalyzeCli())
                .execute(args);
    }
}
