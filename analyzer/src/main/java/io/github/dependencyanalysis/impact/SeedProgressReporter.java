package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

// Wiki: wiki/features/impact-tracing.md - Per-QueryNode TRACE progress contract
/** Query-scoped factory for isolated per-QueryNode TRACE progress trackers. */
final class SeedProgressReporter implements AutoCloseable {

    /** Production heartbeat interval. */
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10L);

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Module impact-query context. */
    private final DiagnosticContext context;

    /** Monotonic time source. */
    private final LongSupplier nanoTime;

    /** Query-scoped scheduler, null when TRACE is disabled. */
    private final HeartbeatScheduler scheduler;

    /** Per-QueryNode heartbeat interval. */
    private final long heartbeatNanos;

    private SeedProgressReporter(
            final DiagnosticLog log,
            final DiagnosticContext diagnosticContext,
            final LongSupplier clock,
            final HeartbeatScheduler heartbeatScheduler,
            final Duration interval) {
        diagnostics = Objects.requireNonNull(log, "log");
        context = Objects.requireNonNull(diagnosticContext, "context");
        nanoTime = Objects.requireNonNull(clock, "clock");
        scheduler = heartbeatScheduler;
        heartbeatNanos = Objects.requireNonNull(interval, "interval")
                .toNanos();
    }

    /**
     * Opens one reporter; non-TRACE runs allocate no scheduler.
     *
     * @param diagnostics diagnostics
     * @param context module impact-query context
     * @return query-scoped reporter
     */
    static SeedProgressReporter open(
            final DiagnosticLog diagnostics,
            final DiagnosticContext context) {
        if (!diagnostics.getVerbosity().includes(LogVerbosity.TRACE)) {
            return new SeedProgressReporter(diagnostics, context,
                    System::nanoTime, null, HEARTBEAT_INTERVAL);
        }
        final ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    final Thread thread = new Thread(runnable,
                            "dependency-analyzer-query-node-progress");
                    thread.setDaemon(true);
                    return thread;
                });
        return new SeedProgressReporter(diagnostics, context,
                System::nanoTime, new ExecutorHeartbeatScheduler(executor),
                HEARTBEAT_INTERVAL);
    }

    /**
     * Injectable construction for deterministic tests.
     *
     * @param diagnostics diagnostics
     * @param context module impact-query context
     * @param nanoTime monotonic clock
     * @param scheduler injectable scheduler
     * @param interval heartbeat interval
     * @return query-scoped reporter
     */
    static SeedProgressReporter create(
            final DiagnosticLog diagnostics,
            final DiagnosticContext context,
            final LongSupplier nanoTime,
            final HeartbeatScheduler scheduler,
            final Duration interval) {
        if (!diagnostics.getVerbosity().includes(LogVerbosity.TRACE)) {
            return new SeedProgressReporter(diagnostics, context, nanoTime,
                    null, interval);
        }
        return new SeedProgressReporter(diagnostics, context, nanoTime,
                scheduler, interval);
    }

    /**
     * Starts one QueryNode query with a fresh clock and metrics.
     *
     * @param ordinal stable Module-local QueryNode ordinal
     * @param node exact QueryNode
     * @param evidenceSeeds evidence bindings consumed by the query
     * @return isolated QueryNode tracker
     */
    SeedProgressTracker startQueryNode(
            final long ordinal,
            final QueryNode node,
            final int evidenceSeeds) {
        if (scheduler == null) {
            return SeedProgressTracker.disabled();
        }
        final NodeProgress progress = NodeProgress.from(node);
        diagnostics.transientLog(context.withPhase("REVERSE_BFS"),
                DiagnosticLevel.TRACE,
                LogVerbosity.TRACE,
                "event=query-node-started; queryNodeOrdinal=" + ordinal
                        + "; evidenceSeeds=" + evidenceSeeds
                        + "; elapsedMs=0; visited=0; edgeChecks=0"
                        + "; prunedEdges=0; "
                        + progress.render("recent"));
        final SeedProgressTracker tracker = new SeedProgressTracker(
                new TrackerConfiguration(diagnostics, context, nanoTime,
                        scheduler, heartbeatNanos),
                ordinal, evidenceSeeds, progress);
        tracker.schedule();
        return tracker;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    /** Minimal scheduling boundary, injectable without real-time waits. */
    interface HeartbeatScheduler extends AutoCloseable {

        /**
         * Schedules a fixed-delay heartbeat action.
         *
         * @param action heartbeat action
         * @param initialDelay initial delay
         * @param period fixed period
         * @param unit delay unit
         * @return cancellable schedule
         */
        Cancellable scheduleWithFixedDelay(
                Runnable action, long initialDelay, long period,
                TimeUnit unit);

        @Override
        void close();
    }

    /** Cancellable heartbeat schedule. */
    interface Cancellable {

        /** Cancels future heartbeats. */
        void cancel();
    }

    /** ScheduledExecutorService adapter. */
    private static final class ExecutorHeartbeatScheduler
            implements HeartbeatScheduler {

        /** Owned executor. */
        private final ScheduledExecutorService executor;

        ExecutorHeartbeatScheduler(final ScheduledExecutorService value) {
            executor = value;
        }

        @Override
        public Cancellable scheduleWithFixedDelay(
                final Runnable action,
                final long initialDelay,
                final long period,
                final TimeUnit unit) {
            final ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                    action, initialDelay, period, unit);
            return () -> future.cancel(false);
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}

/**
 * Shared immutable services used by one seed tracker.
 *
 * @param diagnostics diagnostics
 * @param context module context
 * @param nanoTime monotonic clock
 * @param scheduler heartbeat scheduler
 * @param heartbeatNanos heartbeat interval in nanoseconds
 */
record TrackerConfiguration(
        DiagnosticLog diagnostics,
        DiagnosticContext context,
        LongSupplier nanoTime,
        SeedProgressReporter.HeartbeatScheduler scheduler,
        long heartbeatNanos) {
}

/** Thread-safe metrics for exactly one QueryNode query. */
final class SeedProgressTracker implements AutoCloseable {

    /** Shared disabled tracker; it owns no mutable query state. */
    private static final SeedProgressTracker DISABLED =
            new SeedProgressTracker();

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Module context. */
    private final DiagnosticContext context;

    /** Per-QueryNode monotonic clock. */
    private final LongSupplier nanoTime;

    /** Query scheduler. */
    private final SeedProgressReporter.HeartbeatScheduler scheduler;

    /** Interval. */
    private final long heartbeatNanos;

    /** Per-QueryNode start; never shared with another query. */
    private final long startedNanos;

    /** Log identity. */
    private final long ordinal;

    /** Evidence bindings consumed by this QueryNode. */
    private final int evidenceSeeds;

    /** Per-QueryNode visited count. */
    private final AtomicInteger visited = new AtomicInteger();

    /** Per-QueryNode checked predecessor edges. */
    private final AtomicLong edgeChecks = new AtomicLong();

    /** Per-QueryNode pruned predecessor edges. */
    private final AtomicLong prunedEdges = new AtomicLong();

    /** Per-QueryNode most recently processed node. */
    private final AtomicReference<NodeProgress> recentNode;

    /** Per-QueryNode current phase. */
    private final AtomicReference<QueryNodePhase> phase =
            new AtomicReference<>(QueryNodePhase.REVERSE_BFS);

    /** Per-QueryNode heartbeat ordinal. */
    private final AtomicLong heartbeats = new AtomicLong();

    /** Active state. */
    private final AtomicBoolean active = new AtomicBoolean(true);

    /** Serializes heartbeat completion ordering. */
    private final Object lifecycle = new Object();

    /** Active heartbeat schedule. */
    private volatile SeedProgressReporter.Cancellable heartbeatSchedule;

    private SeedProgressTracker() {
        diagnostics = null;
        context = null;
        nanoTime = null;
        scheduler = null;
        heartbeatNanos = 0L;
        startedNanos = 0L;
        ordinal = 0L;
        evidenceSeeds = 0;
        recentNode = new AtomicReference<>();
        active.set(false);
    }

    SeedProgressTracker(
            final TrackerConfiguration configuration,
            final long queryNodeOrdinal,
            final int bindings,
            final NodeProgress queryNode) {
        diagnostics = configuration.diagnostics();
        context = configuration.context();
        nanoTime = configuration.nanoTime();
        scheduler = configuration.scheduler();
        heartbeatNanos = configuration.heartbeatNanos();
        ordinal = queryNodeOrdinal;
        evidenceSeeds = bindings;
        recentNode = new AtomicReference<>(queryNode);
        startedNanos = nanoTime.getAsLong();
    }

    static SeedProgressTracker disabled() {
        return DISABLED;
    }

    void schedule() {
        if (!active.get()) {
            return;
        }
        heartbeatSchedule = scheduler.scheduleWithFixedDelay(this::heartbeat,
                heartbeatNanos, heartbeatNanos, TimeUnit.NANOSECONDS);
    }

    void reverseProgress(final QueryNode node, final int count) {
        reverseProgress(node, count, 0L, 0L);
    }

    void reverseProgress(
            final QueryNode node,
            final int visitedCount,
            final long checkedEdges,
            final long prunedEdgeCount) {
        if (!active.get()) {
            return;
        }
        phase.set(QueryNodePhase.REVERSE_BFS);
        recentNode.set(NodeProgress.from(node));
        visited.set(visitedCount);
        edgeChecks.set(checkedEdges);
        prunedEdges.set(prunedEdgeCount);
    }

    void visited(final int count) {
        visited(count, 0L, 0L);
    }

    void visited(
            final int visitedCount,
            final long checkedEdges,
            final long prunedEdgeCount) {
        if (active.get()) {
            visited.set(visitedCount);
            edgeChecks.set(checkedEdges);
            prunedEdges.set(prunedEdgeCount);
        }
    }

    void reverseCompleted(final int count) {
        reverseCompleted(count, 0L, 0L);
    }

    void reverseCompleted(
            final int visitedCount,
            final long checkedEdges,
            final long prunedEdgeCount) {
        if (!active.get()) {
            return;
        }
        visited(visitedCount, checkedEdges, prunedEdgeCount);
        phase.set(QueryNodePhase.PATH_MATERIALIZATION);
    }

    void pathMaterialization(final QueryNode node) {
        if (!active.get()) {
            return;
        }
        phase.set(QueryNodePhase.PATH_MATERIALIZATION);
        recentNode.set(NodeProgress.from(node));
    }

    void representativeSelection(final QueryNode node) {
        if (!active.get()) {
            return;
        }
        phase.set(QueryNodePhase.REPRESENTATIVE_SELECTION);
        recentNode.set(NodeProgress.from(node));
    }

    void complete() {
        synchronized (lifecycle) {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            cancel();
            diagnostics.transientLog(context.withPhase(phase.get().name()),
                    DiagnosticLevel.TRACE,
                    LogVerbosity.TRACE,
                    "event=query-node-completed; queryNodeOrdinal=" + ordinal
                            + "; evidenceSeeds=" + evidenceSeeds
                            + "; elapsedMs=" + elapsedMillis()
                            + "; visited=" + visited.get()
                            + "; edgeChecks=" + edgeChecks.get()
                            + "; prunedEdges="
                            + prunedEdges.get() + "; "
                            + recentNode.get().render("recent"));
        }
    }

    private void heartbeat() {
        synchronized (lifecycle) {
            if (!active.get()) {
                return;
            }
            final long heartbeat = heartbeats.incrementAndGet();
            final NodeProgress node = recentNode.get();
            diagnostics.transientLog(context.withPhase(phase.get().name()),
                    DiagnosticLevel.TRACE,
                    LogVerbosity.TRACE,
                    "event=query-node-progress; queryNodeOrdinal=" + ordinal
                            + "; evidenceSeeds=" + evidenceSeeds
                            + "; heartbeat=" + heartbeat
                            + "; elapsedMs=" + elapsedMillis()
                            + "; visited=" + visited.get()
                            + "; edgeChecks=" + edgeChecks.get()
                            + "; prunedEdges="
                            + prunedEdges.get()
                            + "; " + node.render("recent"));
        }
    }

    private long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(
                nanoTime.getAsLong() - startedNanos);
    }

    private void cancel() {
        final SeedProgressReporter.Cancellable scheduled = heartbeatSchedule;
        if (scheduled != null) {
            scheduled.cancel();
        }
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (active.compareAndSet(true, false)) {
                cancel();
            }
        }
    }

    /** Single-QueryNode progress phases. */
    private enum QueryNodePhase {
        /** Reverse predecessor traversal. */
        REVERSE_BFS,
        /** Ordered node path recovery. */
        PATH_MATERIALIZATION,
        /** Shortest representative selection. */
        REPRESENTATIVE_SELECTION
    }
}

/**
 * Immutable node identity copied by the analysis thread for heartbeats.
 *
 * @param graphNodeId WALA graph-local node id
 * @param methodOwner JVM method owner
 * @param methodName method name
 * @param methodDescriptor JVM descriptor
 * @param origin code origin
 * @param source class source identity
 * @param context printable WALA Context
 */
record NodeProgress(
        int graphNodeId,
        String methodOwner,
        String methodName,
        String methodDescriptor,
        String origin,
        String source,
        String context) {

    static NodeProgress from(final QueryNode node) {
        final MethodId method = node.methodId();
        if (node instanceof WalaQueryNode wala) {
            return new NodeProgress(wala.walaNode().getGraphNodeId(),
                    method.owner(), method.name(), method.descriptor(),
                    node.origin().name(), method.sourceId(),
                    String.valueOf(wala.walaNode().getContext()));
        }
        if (node instanceof SnapshotQueryNode snapshot) {
            return new NodeProgress(snapshot.graphNodeId(), method.owner(),
                    method.name(), method.descriptor(), node.origin().name(),
                    method.sourceId(), snapshot.context());
        }
        return new NodeProgress(-1, method.owner(), method.name(),
                method.descriptor(), node.origin().name(), method.sourceId(),
                "<unknown>");
    }

    String render(final String prefix) {
        final String name = prefix.isEmpty() ? "" : prefix;
        return key(name, "GraphNodeId") + "=" + graphNodeId
                + "; " + key(name, "MethodOwner") + "=" + clean(methodOwner)
                + "; " + key(name, "MethodName") + "=" + clean(methodName)
                + "; " + key(name, "MethodDescriptor") + "="
                + clean(methodDescriptor)
                + "; " + key(name, "Origin") + "=" + clean(origin)
                + "; " + key(name, "Source") + "=" + clean(source)
                + "; " + key(name, "Context") + "=" + clean(context);
    }

    private String key(final String prefix, final String suffix) {
        if (prefix.isEmpty()) {
            return Character.toLowerCase(suffix.charAt(0))
                    + suffix.substring(1);
        }
        return prefix + suffix;
    }

    private String clean(final String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
