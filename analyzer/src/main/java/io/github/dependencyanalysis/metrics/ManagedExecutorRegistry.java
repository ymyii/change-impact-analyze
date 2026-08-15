package io.github.dependencyanalysis.metrics;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates, registers, snapshots, and closes Analyzer-owned fixed pools. */
public final class ManagedExecutorRegistry {

    /** Active pools by stable business name. */
    private final ConcurrentHashMap<String, ThreadPoolExecutor> pools =
            new ConcurrentHashMap<>();

    /** Worker thread sequence. */
    private final AtomicInteger threadSequence = new AtomicInteger();

    /**
     * Creates and registers a fixed pool.
     *
     * @param name stable pool name
     * @param workers fixed worker count
     * @return managed pool handle
     */
    public ManagedExecutor fixed(final String name, final int workers) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pool name must not be blank");
        }
        if (workers < 1) {
            throw new IllegalArgumentException("Pool workers must be >= 1");
        }
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workers, workers, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> new Thread(
                        runnable, "dependency-analyzer-" + name + "-"
                                + threadSequence.incrementAndGet()));
        if (pools.putIfAbsent(name, executor) != null) {
            executor.shutdownNow();
            throw new IllegalStateException(
                    "Executor pool is already registered: " + name);
        }
        return new ManagedExecutor(name, executor, this);
    }

    /** @return stable snapshot of all currently registered pools */
    public List<ExecutorMetrics> snapshot() {
        return pools.entrySet().stream()
                .map(entry -> ExecutorMetrics.capture(
                        entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ExecutorMetrics::name))
                .toList();
    }

    private void unregister(
            final String name,
            final ThreadPoolExecutor executor) {
        pools.remove(name, executor);
    }

    /** Managed pool lifetime. */
    public static final class ManagedExecutor implements AutoCloseable {

        /** Pool name. */
        private final String name;

        /** Pool. */
        private final ThreadPoolExecutor executor;

        /** Owner. */
        private final ManagedExecutorRegistry registry;

        /** Closed flag. */
        private boolean closed;

        ManagedExecutor(
                final String poolName,
                final ThreadPoolExecutor pool,
                final ManagedExecutorRegistry owner) {
            name = poolName;
            executor = pool;
            registry = owner;
        }

        /** @return underlying executor */
        public ThreadPoolExecutor executor() {
            return executor;
        }

        /** Initiates immediate shutdown while retaining metrics visibility. */
        public void shutdownNow() {
            executor.shutdownNow();
        }

        /** Shuts down and unregisters the pool. */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            executor.shutdownNow();
            registry.unregister(name, executor);
        }
    }

    /**
     * Stable thread pool metric snapshot.
     *
     * @param name pool name
     * @param corePoolSize configured core size
     * @param maximumPoolSize configured maximum size
     * @param poolSize current worker count
     * @param activeCount active worker count
     * @param queuedCount queued operation count
     * @param completedCount completed operation count
     * @param submittedCount total submitted operation count
     * @param shutdown whether shutdown started
     * @param terminated whether termination completed
     */
    public record ExecutorMetrics(
            String name,
            int corePoolSize,
            int maximumPoolSize,
            int poolSize,
            int activeCount,
            int queuedCount,
            long completedCount,
            long submittedCount,
            boolean shutdown,
            boolean terminated) {

        /**
         * Captures one pool without retaining the executor.
         *
         * @param name pool name
         * @param executor executor
         * @return snapshot
         */
        static ExecutorMetrics capture(
                final String name,
                final ThreadPoolExecutor executor) {
            Objects.requireNonNull(executor, "executor");
            return new ExecutorMetrics(name,
                    executor.getCorePoolSize(), executor.getMaximumPoolSize(),
                    executor.getPoolSize(), executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount(),
                    executor.getTaskCount(), executor.isShutdown(),
                    executor.isTerminated());
        }
    }
}
