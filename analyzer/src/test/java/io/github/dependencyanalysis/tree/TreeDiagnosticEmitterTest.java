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
                        + "[-] Stage 2/3: Analysis; status=RUNNING; reactors=1")
                .contains("[INFO][analysis][reactor]"
                        + "[reactor=pom.xml] Maven collection started;"
                        + " progress=1/1; status=RUNNING")
                .contains("[WARN][analysis][reactor]"
                        + "[reactor=pom.xml] Maven collection completed;"
                        + " progress=1/1; status=DEGRADED; modules=0")
                .contains("[WARN][analysis][issue]"
                        + "[reactor=pom.xml] Analysis issue; issue=1;"
                        + " status=DEGRADED;")
                .contains("[WARN][summary][result]"
                        + "[-] Stage 3/3: Summary;"
                        + " status=COMPLETED_WITH_ISSUES; report=/tmp/report")
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
                .contains("[ERROR][analysis][summary][-]"
                        + " Stage 2/3: Analysis; status=SKIPPED;"
                        + " reason=command preflight failed")
                .contains("[ERROR][summary][result]"
                        + "[-] Stage 3/3: Summary; status=FAILED;"
                        + " report=NOT_GENERATED");
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

    @Test
    void summarizesIncompleteClasspathEvidence() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final TreeDiagnosticEmitter emitter = emitter(bytes,
                LogVerbosity.INFO);
        final Path pom = Path.of("pom.xml");
        final ModuleTreeResult module = new ModuleTreeResult(
                pom, "demo:app:1", List.of(), true, "",
                ModuleAnalysisRole.REQUESTED).withClassAnalysis(
                List.of(), List.of("classifier output missing",
                        "source unreadable"));
        final ReactorTreeResult result = new ReactorTreeResult(
                new ReactorDescriptor(pom, "demo:app:1",
                        List.of(pom), List.of()),
                List.of(module), ReactorStatus.DEGRADED,
                "incomplete classpath");

        emitter.reactorCompleted(1, 1, result);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[WARN][analysis][classpath-incomplete]"
                        + "[reactor=pom.xml] Classpath incomplete;"
                        + " modules=1; issues=2");
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
