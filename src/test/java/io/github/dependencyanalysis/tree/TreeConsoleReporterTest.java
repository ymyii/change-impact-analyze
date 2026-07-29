package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightReport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Tree three-stage console and analysis issue tests. */
class TreeConsoleReporterTest {

    /** Short heartbeat used by the unit test. */
    private static final long TEST_HEARTBEAT_MILLIS = 5L;

    /** Time allowed for multiple test heartbeats. */
    private static final long HEARTBEAT_WAIT_MILLIS = 40L;

    /** Required production heartbeat interval. */
    private static final long PRODUCTION_HEARTBEAT_MILLIS =
            10_000L;

    @Test
    void productionHeartbeatIsTenSeconds() {
        assertThat(TreeConsoleReporter.HEARTBEAT_MILLIS)
                .isEqualTo(PRODUCTION_HEARTBEAT_MILLIS);
    }

    @Test
    void emitsThreeStagesHeartbeatIssuesAndFinalStatus()
            throws Exception {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        final TreeConsoleReporter reporter =
                new TreeConsoleReporter(new PrintStream(
                        bytes, true,
                        StandardCharsets.UTF_8),
                        TEST_HEARTBEAT_MILLIS);
        final ReactorTreeResult result = degraded();

        reporter.preflight(new PreflightReport(List.of()));
        reporter.analysisStarted(1);
        try (TreeConsoleReporter.Heartbeat ignored =
                     reporter.reactorStarted(
                             1, 1, "pom.xml")) {
            Thread.sleep(HEARTBEAT_WAIT_MILLIS);
        }
        reporter.reactorCompleted(1, 1, result);
        reporter.analysisCompleted(List.of(
                new TreeAnalysisIssue(
                        "pom.xml", ReactorStatus.DEGRADED,
                        "reduced\n evidence")));
        reporter.summary(new TreeRunSummary(
                TreeReportState.COMPLETED_WITH_ISSUES,
                "/tmp/report"));

        final String text = bytes.toString(
                StandardCharsets.UTF_8);
        assertThat(text)
                .contains("Stage 1/3: Preflight")
                .contains("Stage 2/3: Analysis")
                .contains("[1/1] MAVEN_COLLECTION_START reactor=pom.xml")
                .contains("[1/1] MAVEN_COLLECTION_HEARTBEAT reactor=pom.xml")
                .contains("[1/1] MAVEN_COLLECTION_RESULT status=DEGRADED")
                .contains("Stage 3/3: Summary")
                .contains("status=COMPLETED_WITH_ISSUES")
                .contains("report=/tmp/report")
                .contains("analysisIssues=1")
                .contains("reason=reduced evidence");
        assertThat(summaryLines(text)).containsExactly(
                "Stage 3/3: Summary",
                "status=COMPLETED_WITH_ISSUES",
                "report=/tmp/report");
    }

    @Test
    void reportsFailedPreflightWithoutReport() {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        final TreeConsoleReporter reporter =
                new TreeConsoleReporter(new PrintStream(
                        bytes, true,
                        StandardCharsets.UTF_8));

        reporter.analysisSkipped("command preflight failed");
        reporter.summary(new TreeRunSummary(
                TreeReportState.FAILED,
                "NOT_GENERATED"));

        final String text = bytes.toString(
                StandardCharsets.UTF_8);
        assertThat(text)
                .contains("Stage 2/3: Analysis")
                .contains("SKIPPED: command preflight failed")
                .contains("Stage 3/3: Summary")
                .contains("status=FAILED")
                .contains("report=NOT_GENERATED")
                .doesNotContain("reactors=")
                .doesNotContain("analysisIssues=");
        assertThat(summaryLines(text)).containsExactly(
                "Stage 3/3: Summary",
                "status=FAILED",
                "report=NOT_GENERATED");
    }

    @Test
    void analysisIssueRejectsSuccess() {
        assertThatThrownBy(() -> new TreeAnalysisIssue(
                "pom.xml", ReactorStatus.SUCCESS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS");
    }

    private ReactorTreeResult degraded() {
        final Path pom = Path.of("pom.xml");
        return new ReactorTreeResult(
                new ReactorDescriptor(pom, "g:a:1",
                        List.of(pom), List.of()),
                List.of(), ReactorStatus.DEGRADED,
                "reduced evidence");
    }

    private List<String> summaryLines(
            final String text) {
        return text.substring(text.indexOf(
                        "Stage 3/3: Summary"))
                .lines().toList();
    }
}
