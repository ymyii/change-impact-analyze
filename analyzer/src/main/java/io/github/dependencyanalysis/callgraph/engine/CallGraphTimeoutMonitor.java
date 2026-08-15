package io.github.dependencyanalysis.callgraph.engine;

import com.ibm.wala.util.MonitorUtil;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Supplies WALA cooperative cancellation without a background thread. */
public final class CallGraphTimeoutMonitor
        implements MonitorUtil.IProgressMonitor {

    /** Optional deadline. */
    private final long deadlineNanos;

    /** Explicit cancellation flag. */
    private final AtomicBoolean canceled = new AtomicBoolean();

    /**
     * Creates a timeout monitor.
     *
     * @param timeout timeout, zero for unlimited
     */
    public CallGraphTimeoutMonitor(final Duration timeout) {
        deadlineNanos = timeout.isZero()
                ? Long.MAX_VALUE
                : System.nanoTime() + timeout.toNanos();
    }

    @Override
    public void beginTask(final String ignoredName, final int totalWork) {
        // WALA progress units are intentionally not surfaced.
    }

    @Override
    public void subTask(final String ignoredName) {
        // WALA progress callback names are implementation details.
    }

    @Override
    public void cancel() {
        canceled.set(true);
    }

    @Override
    public boolean isCanceled() {
        return canceled.get() || System.nanoTime() >= deadlineNanos;
    }

    @Override
    public void done() {
        // The caller owns the build lifecycle.
    }

    @Override
    public void worked(final int units) {
        // Progress is not retained or logged.
    }

    @Override
    public String getCancelMessage() {
        return isTimedOut()
                ? "WALA call graph timeout reached"
                : "WALA call graph canceled";
    }

    /** @return true when the deadline caused cancellation */
    public boolean isTimedOut() {
        return System.nanoTime() >= deadlineNanos;
    }
}
