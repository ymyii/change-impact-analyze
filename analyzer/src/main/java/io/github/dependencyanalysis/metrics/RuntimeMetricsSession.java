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

    /** Production interval. */
    public static final Duration INTERVAL = Duration.ofSeconds(10L);

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

    /** Closed flag. */
    private boolean closed;

    private RuntimeMetricsSession(
            final DiagnosticLog diagnosticLog,
            final ManagedExecutorRegistry registry,
            final MemoryMXBean memoryBean,
            final ScheduledExecutorService scheduledExecutor,
            final Duration interval) {
        log = Objects.requireNonNull(diagnosticLog, "log");
        executors = Objects.requireNonNull(registry, "executors");
        memory = Objects.requireNonNull(memoryBean, "memory");
        scheduler = scheduledExecutor;
        if (scheduler != null) {
            sample();
            scheduler.scheduleWithFixedDelay(this::sample,
                    interval.toMillis(), interval.toMillis(),
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
                    ManagementFactory.getMemoryMXBean(), null, INTERVAL);
        }
        final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    final Thread thread = new Thread(runnable,
                            "dependency-analyzer-runtime-metrics");
                    thread.setDaemon(true);
                    return thread;
                });
        return new RuntimeMetricsSession(log, registry,
                ManagementFactory.getMemoryMXBean(), scheduler, INTERVAL);
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
                scheduledExecutor, interval);
    }

    /** @return Analyzer-owned pool registry */
    public ManagedExecutorRegistry executors() {
        return executors;
    }

    /** Samples heap and all currently registered business pools. */
    synchronized void sample() {
        if (closed) {
            return;
        }
        final long sample = samples.incrementAndGet();
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos);
        try {
            emitHeap(sample, elapsedMillis, memory.getHeapMemoryUsage());
            for (ManagedExecutorRegistry.ExecutorMetrics pool
                    : executors.snapshot()) {
                emitPool(sample, elapsedMillis, pool);
            }
        } catch (RuntimeException failure) {
            final DiagnosticContext context = DiagnosticContext.of(
                    "runtime-metrics", "sampler")
                    .with("sample", sample)
                    .with("elapsedMs", elapsedMillis);
            log.transientLog(context, DiagnosticLevel.TRACE,
                    LogVerbosity.TRACE,
                    "Sampling failed: " + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
        }
    }

    private void emitHeap(
            final long sample,
            final long elapsedMillis,
            final MemoryUsage heap) {
        final DiagnosticContext context = DiagnosticContext.of(
                "runtime-metrics", "heap")
                .with("elapsedMs", elapsedMillis)
                .with("sample", sample)
                .with("heapUsedMiB", mebibytes(heap.getUsed()))
                .with("heapCommittedMiB", mebibytes(heap.getCommitted()))
                .with("heapMaxMiB", mebibytes(heap.getMax()));
        log.transientLog(context, DiagnosticLevel.TRACE,
                LogVerbosity.TRACE, "Runtime metrics snapshot");
    }

    private void emitPool(
            final long sample,
            final long elapsedMillis,
            final ManagedExecutorRegistry.ExecutorMetrics pool) {
        final DiagnosticContext context = DiagnosticContext.of(
                "runtime-metrics", "thread-pool")
                .with("elapsedMs", elapsedMillis)
                .with("sample", sample)
                .with("pool", pool.name())
                .with("core", pool.corePoolSize())
                .with("max", pool.maximumPoolSize())
                .with("size", pool.poolSize())
                .with("active", pool.activeCount())
                .with("queued", pool.queuedTaskCount())
                .with("completed", pool.completedTaskCount())
                .with("tasks", pool.taskCount())
                .with("shutdown", pool.shutdown())
                .with("terminated", pool.terminated());
        log.transientLog(context, DiagnosticLevel.TRACE,
                LogVerbosity.TRACE, "Runtime metrics snapshot");
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
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
