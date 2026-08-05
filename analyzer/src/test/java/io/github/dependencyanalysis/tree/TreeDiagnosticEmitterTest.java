package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticLogFormatter;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.preflight.PreflightReport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests Tree semantic events emitted through the unified Diagnostic log. */
class TreeDiagnosticEmitterTest {

    /** Test timezone offset hours. */
    private static final int TEST_OFFSET_HOURS = 8;

    /** Fixed timestamp. */
    private static final Instant NOW = Instant.parse(
            "2026-08-05T06:30:01.123Z");

    @Test
    void emitsPrefixedThreeStagesIssuesAndFinalStatus() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final TreeDiagnosticEmitter emitter = emitter(bytes,
                LogVerbosity.INFO);
        final ReactorTreeResult result = degraded();

        emitter.preflight(new PreflightReport(List.of()));
        emitter.analysisStarted(1);
        emitter.reactorStarted(1, 1, "pom.xml");
        emitter.reactorCompleted(1, 1, result);
        emitter.analysisCompleted(List.of(new TreeAnalysisIssue(
                "pom.xml", ReactorStatus.DEGRADED,
                "reduced\n evidence")));
        emitter.summary(new TreeRunSummary(
                TreeReportState.COMPLETED_WITH_ISSUES, "/tmp/report"));

        final String text = bytes.toString(StandardCharsets.UTF_8);
        assertThat(text)
                .contains("[INFO][preflight][summary][-]"
                        + " Stage 1/3: Preflight")
                .contains("[INFO][analysis][summary]"
                        + "[status=RUNNING;reactors=1] Stage 2/3: Analysis")
                .contains("[INFO][analysis][reactor]"
                        + "[reactor=pom.xml;progress=1/1;status=RUNNING]"
                        + " Maven collection started")
                .contains("[WARN][analysis][reactor]"
                        + "[reactor=pom.xml;progress=1/1;status=DEGRADED;"
                        + "modules=0] Maven collection completed")
                .contains("[WARN][analysis][issue]"
                        + "[reactor=pom.xml;status=DEGRADED;")
                .contains("[WARN][summary][result]"
                        + "[status=COMPLETED_WITH_ISSUES;report=/tmp/report]"
                        + " Stage 3/3: Summary")
                .doesNotContain("heartbeat", "HEARTBEAT");
        assertThat(text.lines()).allMatch(line -> line.matches(
                "^\\[2026-08-05T14:30:01\\.123\\+08:00]"
                        + "\\[(INFO|WARN|ERROR)]\\[[^]]+]\\[[^]]+]"
                        + "\\[[^]]+].*"));
    }

    @Test
    void reportsFailedPreflightWithoutReport() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final TreeDiagnosticEmitter emitter = emitter(bytes,
                LogVerbosity.INFO);

        emitter.analysisSkipped("command preflight failed");
        emitter.summary(new TreeRunSummary(
                TreeReportState.FAILED, "NOT_GENERATED"));

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[ERROR][analysis][summary][status=SKIPPED]"
                        + " Stage 2/3: Analysis; command preflight failed")
                .contains("[ERROR][summary][result]"
                        + "[status=FAILED;report=NOT_GENERATED]"
                        + " Stage 3/3: Summary");
    }

    @Test
    void analysisIssueRejectsSuccess() {
        assertThatThrownBy(() -> new TreeAnalysisIssue(
                "pom.xml", ReactorStatus.SUCCESS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS");
    }

    @Test
    void verbosityFiltersDebugAndTraceMessages() {
        final ByteArrayOutputStream info = new ByteArrayOutputStream();
        final ByteArrayOutputStream debug = new ByteArrayOutputStream();
        final ByteArrayOutputStream trace = new ByteArrayOutputStream();

        emitter(info, LogVerbosity.INFO).debug("debug-message");
        emitter(debug, LogVerbosity.DEBUG).debug("debug-message");
        emitter(debug, LogVerbosity.DEBUG).trace("trace-message");
        emitter(trace, LogVerbosity.TRACE).trace("trace-message");

        assertThat(info.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(debug.toString(StandardCharsets.UTF_8))
                .contains("[DEBUG][cli][tree][-] debug-message")
                .doesNotContain("trace-message");
        assertThat(trace.toString(StandardCharsets.UTF_8))
                .contains("[TRACE][cli][tree][-] trace-message");
    }

    private TreeDiagnosticEmitter emitter(
            final ByteArrayOutputStream bytes,
            final LogVerbosity verbosity) {
        final DiagnosticLog log = new DiagnosticLog(new PrintStream(
                bytes, true, StandardCharsets.UTF_8), verbosity,
                Clock.fixed(NOW, ZoneOffset.ofHours(TEST_OFFSET_HOURS)),
                System::nanoTime, new DiagnosticLogFormatter());
        return new TreeDiagnosticEmitter(log);
    }

    private ReactorTreeResult degraded() {
        final Path pom = Path.of("pom.xml");
        return new ReactorTreeResult(
                new ReactorDescriptor(pom, "g:a:1",
                        List.of(pom), List.of()),
                List.of(), ReactorStatus.DEGRADED,
                "reduced evidence");
    }
}
