package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.util.MonitorUtil;

import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Reports WALA liveness and supplies cooperative timeout cancellation. */
final class CallGraphProgressMonitor
        implements MonitorUtil.IProgressMonitor, AutoCloseable {

    /** Heartbeat interval. */
    static final long HEARTBEAT_SECONDS = 10L;

    /** Bytes per MiB. */
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /** Start time. */
    private final long startedNanos = System.nanoTime();

    /** Optional deadline. */
    private final long deadlineNanos;

    /** WALA work units. */
    private final AtomicLong workUnits = new AtomicLong();

    /** Explicit cancellation flag. */
    private final AtomicBoolean canceled = new AtomicBoolean();

    /** Heartbeat executor. */
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a monitor.
     *
     * @param collector diagnostics
     * @param timeoutSeconds timeout, zero for unlimited
     */
    CallGraphProgressMonitor(
            final DiagnosticCollector collector,
            final long timeoutSeconds) {
        this(collector,
                timeoutSeconds == 0
                        ? Duration.ZERO
                        : Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(HEARTBEAT_SECONDS));
    }

    /**
     * Creates a monitor with injectable timing for unit tests.
     *
     * @param collector diagnostics
     * @param timeout timeout, zero for unlimited
     * @param heartbeatInterval heartbeat interval
     */
    CallGraphProgressMonitor(
            final DiagnosticCollector collector,
            final Duration timeout,
            final Duration heartbeatInterval) {
        diagnostics = collector;
        deadlineNanos = timeout.isZero()
                ? Long.MAX_VALUE
                : startedNanos
                + timeout.toNanos();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    final Thread thread = new Thread(
                            runnable, "call-graph-heartbeat");
                    thread.setDaemon(true);
                    return thread;
                });
        scheduler.scheduleAtFixedRate(
                this::heartbeat,
                heartbeatInterval.toMillis(),
                heartbeatInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void beginTask(final String task, final int totalWork) {
        workUnits.set(0L);
    }

    @Override
    public void subTask(final String subTask) {
        // WALA subtask names are implementation details.
    }

    @Override
    public void cancel() {
        canceled.set(true);
    }

    @Override
    public boolean isCanceled() {
        return canceled.get()
                || System.nanoTime() >= deadlineNanos;
    }

    @Override
    public void done() {
        // Lifecycle is controlled by close().
    }

    @Override
    public void worked(final int units) {
        workUnits.addAndGet(units);
    }

    @Override
    public String getCancelMessage() {
        return System.nanoTime() >= deadlineNanos
                ? "WALA call graph timeout reached"
                : "WALA call graph canceled";
    }

    /** @return true when timeout caused cancellation */
    boolean isTimedOut() {
        return System.nanoTime() >= deadlineNanos;
    }

    private void heartbeat() {
        final Runtime runtime = Runtime.getRuntime();
        final long used = runtime.totalMemory()
                - runtime.freeMemory();
        final long elapsed = Duration.ofNanos(
                System.nanoTime() - startedNanos).toSeconds();
        diagnostics.info("call-graph",
                "WALA heartbeat: elapsed=" + elapsed
                        + "s, heap=" + toMiB(used)
                        + "/" + toMiB(runtime.maxMemory())
                        + " MiB, progress=" + workUnits.get());
    }

    private long toMiB(final long bytes) {
        return bytes / BYTES_PER_MIB;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
