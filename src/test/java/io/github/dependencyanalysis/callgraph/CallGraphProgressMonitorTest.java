package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests WALA timeout cancellation and progress units. */
class CallGraphProgressMonitorTest {

    /** Wait long enough for the millisecond test deadline. */
    private static final long TIMEOUT_WAIT_MILLIS = 20L;

    /** Short heartbeat interval for unit tests. */
    private static final long HEARTBEAT_MILLIS = 10L;

    /** Fixture work units. */
    private static final int WORK_UNITS = 3;

    @Test
    void zeroTimeoutDoesNotCancel() {
        final DiagnosticCollector diagnostics = diagnostics();
        try (CallGraphProgressMonitor monitor =
                     new CallGraphProgressMonitor(
                             diagnostics, 0L)) {
            monitor.worked(1);
            assertThat(monitor.isCanceled()).isFalse();
        }
    }

    @Test
    void positiveTimeoutCancelsCooperatively()
            throws Exception {
        final DiagnosticCollector diagnostics = diagnostics();
        try (CallGraphProgressMonitor monitor =
                     new CallGraphProgressMonitor(
                             diagnostics,
                             Duration.ofMillis(1),
                             Duration.ofDays(1))) {
            Thread.sleep(TIMEOUT_WAIT_MILLIS);
            assertThat(monitor.isCanceled()).isTrue();
            assertThat(monitor.isTimedOut()).isTrue();
            assertThat(monitor.getCancelMessage())
                    .contains("timeout");
        }
    }

    @Test
    void heartbeatReportsElapsedHeapAndProgress()
            throws Exception {
        final DiagnosticCollector diagnostics = diagnostics();
        try (CallGraphProgressMonitor monitor =
                     new CallGraphProgressMonitor(
                             diagnostics, Duration.ZERO,
                             Duration.ofMillis(HEARTBEAT_MILLIS))) {
            monitor.worked(WORK_UNITS);
            Thread.sleep(TIMEOUT_WAIT_MILLIS);
        }
        assertThat(diagnostics.getEvents())
                .extracting(event -> event.getMessage())
                .anyMatch(message -> message.contains(
                        "WALA heartbeat: elapsed=")
                        && message.contains("heap=")
                        && message.contains(
                        "progress=" + WORK_UNITS));
    }

    private DiagnosticCollector diagnostics() {
        return new DiagnosticCollector(
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
    }
}
