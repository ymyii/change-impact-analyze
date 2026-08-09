package io.github.dependencyanalysis.metrics;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Wiki: wiki/features/cli-preflight-diagnostics.md - TRACE Runtime Metrics 生命周期
/** Command-scoped asynchronous heap and business-pool metric sampler. */
public final class RuntimeMetricsSession implements AutoCloseable {

    /** Production TRACE snapshot interval. */
    public static final Duration INTERVAL = Duration.ofSeconds(10L);

    /** Production heap observation interval. */
    public static final Duration OBSERVATION_INTERVAL =
            Duration.ofMillis(100L);

    /** Bytes per MiB. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /** Log. */
    private final DiagnosticLog log;

    /** Pool registry. */
    private final ManagedExecutorRegistry executors;

    /** Heap source. */
    private final MemoryMXBean memory;

    /** Scheduler, null when disabled. */
    private final ScheduledExecutorService scheduler;

    /** Start time. */
    private final long startedNanos = System.nanoTime();

    /** Sample sequence. */
    private final AtomicLong samples = new AtomicLong();

    /** TRACE snapshot cadence. */
    private final long logIntervalNanos;

    /** Last TRACE snapshot time. */
    private long lastLoggedNanos;

    /** Peak observed heap usage. */
    private long peakHeapUsed;

    /** Peak observed committed heap. */
    private long peakHeapCommitted;

    /** Maximum configured heap observed by the command. */
    private long heapMax = -1L;

    /** Closed flag. */
    private boolean closed;

    private RuntimeMetricsSession(
            final DiagnosticLog diagnosticLog,
            final ManagedExecutorRegistry registry,
            final MemoryMXBean memoryBean,
            final ScheduledExecutorService scheduledExecutor,
            final Duration observationInterval,
            final Duration logInterval) {
        log = Objects.requireNonNull(diagnosticLog, "log");
        executors = Objects.requireNonNull(registry, "executors");
        memory = Objects.requireNonNull(memoryBean, "memory");
        scheduler = scheduledExecutor;
        logIntervalNanos = Objects.requireNonNull(logInterval, "logInterval")
                .toNanos();
        if (scheduler != null) {
            observe(true);
            scheduler.scheduleWithFixedDelay(this::scheduledObserve,
                    observationInterval.toMillis(),
                    observationInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Starts a production session, active only at TRACE verbosity.
     *
     * @param log command log
     * @return session
     */
    public static RuntimeMetricsSession start(final DiagnosticLog log) {
        final ManagedExecutorRegistry registry =
                new ManagedExecutorRegistry();
        if (!log.getVerbosity().includes(LogVerbosity.TRACE)) {
            return new RuntimeMetricsSession(log, registry,
                    ManagementFactory.getMemoryMXBean(), null,
                    OBSERVATION_INTERVAL, INTERVAL);
        }
        final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    final Thread thread = new Thread(runnable,
                            "dependency-analyzer-runtime-metrics");
                    thread.setDaemon(true);
                    return thread;
                });
        return new RuntimeMetricsSession(log, registry,
                ManagementFactory.getMemoryMXBean(), scheduler,
                OBSERVATION_INTERVAL, INTERVAL);
    }

    /**
     * Creates an injectable session for tests.
     *
     * @param log log
     * @param registry pool registry
     * @param memoryBean heap source
     * @param scheduledExecutor scheduler, null to disable
     * @param interval sample interval
     * @return session
     */
    static RuntimeMetricsSession create(
            final DiagnosticLog log,
            final ManagedExecutorRegistry registry,
            final MemoryMXBean memoryBean,
            final ScheduledExecutorService scheduledExecutor,
            final Duration interval) {
        return new RuntimeMetricsSession(log, registry, memoryBean,
                scheduledExecutor, interval, interval);
    }

    /** @return Analyzer-owned pool registry */
    public ManagedExecutorRegistry executors() {
        return executors;
    }

    /** Samples heap and all currently registered business pools. */
    synchronized void sample() {
        observe(true);
    }

    private synchronized void scheduledObserve() {
        observe(false);
    }

    private void observe(final boolean forceLog) {
        if (closed) {
            return;
        }
        final long sample = samples.incrementAndGet();
        final long now = System.nanoTime();
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                now - startedNanos);
        try {
            final MemoryUsage heap = memory.getHeapMemoryUsage();
            peakHeapUsed = Math.max(peakHeapUsed, heap.getUsed());
            peakHeapCommitted = Math.max(peakHeapCommitted,
                    heap.getCommitted());
            heapMax = Math.max(heapMax, heap.getMax());
            if (forceLog || lastLoggedNanos == 0L
                    || now - lastLoggedNanos >= logIntervalNanos) {
                lastLoggedNanos = now;
                emitHeap(sample, elapsedMillis, heap);
                for (ManagedExecutorRegistry.ExecutorMetrics pool
                        : executors.snapshot()) {
                    emitPool(sample, elapsedMillis, pool);
                }
            }
        } catch (RuntimeException failure) {
            final DiagnosticContext context = DiagnosticContext.of(
                    "runtime-metrics", "sampler");
            log.transientLog(context, DiagnosticLevel.TRACE,
                    LogVerbosity.TRACE,
                    "Sampling failed; sample=" + sample
                            + "; elapsedMs=" + elapsedMillis
                            + "; error="
                            + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
        }
    }

    private void emitSummary() {
        final DiagnosticContext context = DiagnosticContext.of(
                "runtime-metrics", "summary");
        log.transientLog(context, DiagnosticLevel.TRACE,
                LogVerbosity.TRACE,
                "Runtime metrics summary; samples=" + samples.get()
                        + "; peakHeapUsedMiB="
                        + mebibytes(peakHeapUsed)
                        + "; peakHeapCommittedMiB="
                        + mebibytes(peakHeapCommitted)
                        + "; heapMaxMiB=" + mebibytes(heapMax));
    }

    private void emitHeap(
            final long sample,
            final long elapsedMillis,
            final MemoryUsage heap) {
        final DiagnosticContext context = DiagnosticContext.of(
                "runtime-metrics", "heap");
        log.transientLog(context, DiagnosticLevel.TRACE,
                LogVerbosity.TRACE,
                "Runtime metrics snapshot; sample=" + sample
                        + "; elapsedMs=" + elapsedMillis
                        + "; heapUsedMiB=" + mebibytes(heap.getUsed())
                        + "; heapCommittedMiB="
                        + mebibytes(heap.getCommitted())
                        + "; heapMaxMiB=" + mebibytes(heap.getMax()));
    }

    private void emitPool(
            final long sample,
            final long elapsedMillis,
            final ManagedExecutorRegistry.ExecutorMetrics pool) {
        final DiagnosticContext context = DiagnosticContext.of(
                "runtime-metrics", "thread-pool")
                .with("pool", pool.name());
        log.transientLog(context, DiagnosticLevel.TRACE,
                LogVerbosity.TRACE,
                "Runtime metrics snapshot; sample=" + sample
                        + "; elapsedMs=" + elapsedMillis
                        + "; core=" + pool.corePoolSize()
                        + "; max=" + pool.maximumPoolSize()
                        + "; size=" + pool.poolSize()
                        + "; active=" + pool.activeCount()
                        + "; queued=" + pool.queuedTaskCount()
                        + "; completed=" + pool.completedTaskCount()
                        + "; tasks=" + pool.taskCount()
                        + "; shutdown=" + pool.shutdown()
                        + "; terminated=" + pool.terminated());
    }

    private String mebibytes(final long bytes) {
        if (bytes < 0L) {
            return "-1.0";
        }
        return String.format(Locale.ROOT, "%.1f", bytes / BYTES_PER_MIB);
    }

    /** Stops sampling and waits for an in-flight sample to finish. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (log.getVerbosity().includes(LogVerbosity.TRACE)) {
            observe(false);
            emitSummary();
        }
        closed = true;
    }
}
