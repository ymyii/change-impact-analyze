package io.github.dependencyanalysis.callgraph.engine;


import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests WALA cooperative timeout cancellation. */
class CallGraphTimeoutMonitorTest {

    /** Wait long enough for the millisecond deadline. */
    private static final long TIMEOUT_WAIT_MILLIS = 20L;

    @Test
    void zeroTimeoutDoesNotCancel() {
        final CallGraphTimeoutMonitor monitor =
                new CallGraphTimeoutMonitor(Duration.ZERO);

        monitor.worked(1);

        assertThat(monitor.isCanceled()).isFalse();
        assertThat(monitor.isTimedOut()).isFalse();
    }

    @Test
    void positiveTimeoutCancelsCooperatively() throws Exception {
        final CallGraphTimeoutMonitor monitor =
                new CallGraphTimeoutMonitor(Duration.ofMillis(1L));

        Thread.sleep(TIMEOUT_WAIT_MILLIS);

        assertThat(monitor.isCanceled()).isTrue();
        assertThat(monitor.isTimedOut()).isTrue();
        assertThat(monitor.getCancelMessage()).contains("timeout");
    }

    @Test
    void explicitCancellationIsNotReportedAsTimeout() {
        final CallGraphTimeoutMonitor monitor =
                new CallGraphTimeoutMonitor(Duration.ZERO);

        monitor.cancel();

        assertThat(monitor.isCanceled()).isTrue();
        assertThat(monitor.isTimedOut()).isFalse();
        assertThat(monitor.getCancelMessage()).contains("canceled");
    }

    @Test
    void timeoutExceptionCarriesMachineReadableKind() {
        final CallGraphException timeout = CallGraphException.timeout(
                "localized diagnostic text");
        final CallGraphException general = new CallGraphException(
                "timed out appears only in presentation text");

        assertThat(timeout.getKind()).isEqualTo(
                CallGraphFailureKind.TIMEOUT);
        assertThat(general.getKind()).isEqualTo(
                CallGraphFailureKind.GENERAL);
    }
}
