package io.github.dependencyanalysis.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unified Diagnostic Console logging. */
class DiagnosticLogTest {

    /** Test timezone offset hours. */
    private static final int TEST_OFFSET_HOURS = 8;

    /** Fixed timestamp. */
    private static final Instant NOW = Instant.parse(
            "2026-08-05T06:30:01.123Z");

    /** Twelve elapsed milliseconds in nanoseconds. */
    private static final long TWELVE_MILLIS_NANOS = 12_000_000L;

    @Test
    void emitsFiveSegmentsToOneDestinationWithElapsedTime() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final AtomicLong ticker = new AtomicLong();
        final DiagnosticLog log = log(bytes, LogVerbosity.INFO, ticker);
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "module").withModule("sample");

        log.startStage(context);
        ticker.set(TWELVE_MILLIS_NANOS);
        log.endStage(context);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[2026-08-05T14:30:01.123+08:00]"
                        + "[INFO][module-analysis][module][module=sample]"
                        + " started")
                .contains("[module=sample] completed; elapsedMs=12")
                .doesNotContain("[module=sample;elapsedMs=");
        assertThat(bytes.toString(StandardCharsets.UTF_8).lines())
                .hasSize(2);
    }

    @Test
    void appendsElapsedTimeToFailureMessageWithoutPrefixAttribute() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final AtomicLong ticker = new AtomicLong();
        final DiagnosticLog log = log(bytes, LogVerbosity.INFO, ticker);
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "module").withModule("sample");

        log.startStage(context);
        ticker.set(TWELVE_MILLIS_NANOS);
        log.failStage(context, "reason=unavailable");

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[module=sample] failed; reason=unavailable;"
                        + " elapsedMs=12")
                .doesNotContain("[module=sample;elapsedMs=");
    }

    @Test
    void phaseDoesNotSplitStageTimerIdentity() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final AtomicLong ticker = new AtomicLong();
        final DiagnosticLog log = log(bytes, LogVerbosity.INFO, ticker);
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "impact-query").withModule("sample");

        log.startStage(context);
        log.info(context.withPhase("REVERSE_BFS"), "progress");
        ticker.set(TWELVE_MILLIS_NANOS);
        log.endStage(context);

        assertThat(context.withPhase("REVERSE_BFS").stableKey())
                .isEqualTo(context.stableKey());
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[phase=REVERSE_BFS;module=sample] progress")
                .contains("[module=sample] completed; elapsedMs=12");
    }

    @Test
    void filtersByVerbosityForAllConsoleLines() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = log(bytes, LogVerbosity.INFO,
                new AtomicLong());
        final DiagnosticContext context = DiagnosticContext.stage("stage");

        log.info(context, "retained");
        log.debug(context, "hidden-debug");
        log.transientLog(context, DiagnosticLevel.WARN,
                LogVerbosity.INFO, "transient-warning");
        log.transientLog(context, DiagnosticLevel.INFO,
                LogVerbosity.DEBUG, "hidden-process-info");

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[INFO][stage][-][-] retained")
                .contains("[WARN][stage][-][-] transient-warning")
                .doesNotContain("hidden-debug", "hidden-process-info");
    }

    @Test
    void prefixesEveryPhysicalMessageAndStackTraceLine() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = log(bytes, LogVerbosity.DEBUG,
                new AtomicLong());
        final DiagnosticContext context = DiagnosticContext.of(
                "pipeline", "failure");

        log.transientLog(context, DiagnosticLevel.INFO,
                LogVerbosity.INFO, "first\nsecond");
        log.transientException(context,
                new IllegalStateException("broken"));

        assertThat(bytes.toString(StandardCharsets.UTF_8).lines())
                .allMatch(line -> line.matches(
                        "^\\[[^]]+]{1}\\[(INFO|DEBUG)]"
                                + "\\[pipeline]\\[failure]\\[-].*"))
                .anyMatch(line -> line.endsWith(" first"))
                .anyMatch(line -> line.endsWith(" second"))
                .anyMatch(line -> line.contains(
                        "IllegalStateException: broken"));
    }

    @Test
    void warnExceptionEmitsWarningAndDebugCauseChain() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = log(bytes, LogVerbosity.DEBUG,
                new AtomicLong());
        final DiagnosticContext context = DiagnosticContext.of(
                "jar-diff", "pair");
        final IllegalStateException cause = new IllegalStateException(
                "root cause");
        final RuntimeException failure = new RuntimeException(
                "comparison failed", cause);

        log.warnException(context, "complete warning", failure);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[WARN][jar-diff][pair][-] complete warning")
                .contains("[DEBUG][jar-diff][pair][-] "
                        + "java.lang.RuntimeException: comparison failed")
                .contains("Caused by: java.lang.IllegalStateException: "
                        + "root cause");
    }

    @Test
    void contextPreservesAttributeInsertionOrder() {
        final DiagnosticContext context = DiagnosticContext.of(
                "analysis", "module")
                .with("pool", "module-analysis")
                .with("check", "jdk8")
                .with("module", "sample");

        assertThat(context.attributes().keySet())
                .containsExactlyElementsOf(List.of("pool", "check", "module"));
    }

    private DiagnosticLog log(
            final ByteArrayOutputStream bytes,
            final LogVerbosity verbosity,
            final AtomicLong ticker) {
        return new DiagnosticLog(new PrintStream(bytes, true,
                StandardCharsets.UTF_8), verbosity,
                Clock.fixed(NOW, ZoneOffset.ofHours(TEST_OFFSET_HOURS)),
                ticker::get,
                new DiagnosticLogFormatter());
    }
}
