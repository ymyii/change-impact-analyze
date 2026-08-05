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

/** Tests unified retained and transient Diagnostic logging. */
class DiagnosticLogTest {

    /** Test timezone offset hours. */
    private static final int TEST_OFFSET_HOURS = 8;

    /** Expected elapsed milliseconds. */
    private static final long EXPECTED_ELAPSED_MILLIS = 12L;

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
                "front", "target-build").withSide("target");

        log.startStage(context);
        ticker.set(TWELVE_MILLIS_NANOS);
        log.endStage(context);

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[2026-08-05T14:30:01.123+08:00]"
                        + "[INFO][front][target-build][side=target]"
                        + " Task started")
                .contains("[side=target;elapsedMs=12] Task completed");
        assertThat(log.getEvents()).hasSize(2);
        assertThat(log.getEvents().get(1).getElapsedMillis())
                .isEqualTo(EXPECTED_ELAPSED_MILLIS);
    }

    @Test
    void filtersByVerbosityAndKeepsTransientLinesOutOfEvents() {
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

        assertThat(log.getEvents()).extracting(DiagnosticEvent::getMessage)
                .containsExactly("retained");
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
    void contextPreservesAttributeInsertionOrder() {
        final DiagnosticContext context = DiagnosticContext.of(
                "analysis", "module")
                .with("zeta", "last")
                .with("side", "target")
                .with("module", "sample");

        assertThat(context.attributes().keySet())
                .containsExactlyElementsOf(List.of("zeta", "side", "module"));
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
