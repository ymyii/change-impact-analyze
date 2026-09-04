package io.github.dependencyanalysis.impact.pruning.cha;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.impact.ImpactException;
import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed ordered, fail-open CHA Impact Path edge-pruning engine. */
public final class ChaImpactPathPruningEngine {

    /** Maximum stable examples retained by each extension. */
    private static final int EXAMPLE_LIMIT = 10;

    /** Whether the fixed registry applies to the active graph. */
    private final boolean applied;

    /** Fixed extension runtimes in evaluation order. */
    private final List<ExtensionRuntime> extensions;

    /**
     * Creates the fixed registry and its shared receiver resolver.
     *
     * @param session frozen Module Call Graph session
     * @param captureExamples TRACE or explicit diagnostics capture request
     */
    public ChaImpactPathPruningEngine(
            final ModuleCallGraphSession session,
            final boolean captureExamples) {
        applied = session.getAlgorithm() == CallGraphAlgorithm.CHA;
        final ChaReceiverTypeResolver resolver =
                new ChaReceiverTypeResolver(session);
        extensions = List.of(
                new ExtensionRuntime(
                        new ChaLocalReceiverInferenceExtension(resolver),
                        captureExamples));
    }

    /**
     * Applies every fixed extension to one predecessor edge.
     *
     * @param caller predecessor being considered
     * @param callee current reverse-search node
     * @return false as soon as any extension proves the edge infeasible
     */
    public boolean shouldTraverse(
            final CGNode caller,
            final CGNode callee) {
        if (!applied) {
            return true;
        }
        interrupted();
        final PruningEdge edge = new PruningEdge(caller, callee);
        for (ExtensionRuntime extension : extensions) {
            if (extension.evaluate(edge).decision()
                    == PruningDecision.PROVEN_INFEASIBLE) {
                return false;
            }
        }
        return true;
    }

    /** @return immutable fixed-order execution evidence */
    public ImpactPathPruningSummary summary() {
        if (!applied) {
            return ImpactPathPruningSummary.notAppliedNonCha();
        }
        return new ImpactPathPruningSummary(extensions.stream()
                .map(ExtensionRuntime::summary).toList());
    }

    private void interrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new ImpactException("Impact Path pruning interrupted");
        }
    }

    /** Cached, measured execution wrapper for one built-in extension. */
    static final class ExtensionRuntime {

        /** Built-in extension. */
        private final ImpactPathPruningExtension extension;

        /** Whether stable examples are formatted. */
        private final boolean captureExamples;

        /** Evaluation cache. */
        private final ConcurrentMap<Object, PruningEvaluation> cache =
                new ConcurrentHashMap<>();

        /** Deterministic bounded examples. */
        private final TreeMap<String,
                ImpactPathPruningSummary.EdgeExample> examples =
                new TreeMap<>();

        ExtensionRuntime(
                final ImpactPathPruningExtension value,
                final boolean capture) {
            extension = value;
            captureExamples = capture;
        }

        PruningEvaluation evaluate(final PruningEdge edge) {
            final ExtensionCounters counters = extension.counters();
            counters.request();
            final AtomicBoolean evaluated = new AtomicBoolean();
            final PruningEvaluation result = cache.computeIfAbsent(
                    extension.cacheKey(edge), ignored -> {
                        evaluated.set(true);
                        counters.uniqueEvaluation();
                        final PruningEvaluation outcome =
                                evaluateSafely(edge);
                        counters.record(outcome.decision());
                        if (captureExamples) {
                            record(edge, outcome);
                        }
                        return outcome;
                    });
            if (!evaluated.get()) {
                counters.cacheHit();
            }
            return result;
        }

        private PruningEvaluation evaluateSafely(
                final PruningEdge edge) {
            try {
                return extension.evaluate(edge);
            } catch (RuntimeException exception) {
                if (Thread.currentThread().isInterrupted()
                        || exception instanceof ImpactException
                        && exception.getMessage() != null
                        && exception.getMessage().contains("interrupted")) {
                    throw exception;
                }
                extension.counters().failOpenError();
                return PruningEvaluation.unknown(-1,
                        "fail-open-"
                                + exception.getClass().getSimpleName(),
                        "unknown");
            }
        }

        private void record(
                final PruningEdge edge,
                final PruningEvaluation outcome) {
            final ImpactPathPruningSummary.EdgeExample example =
                    new ImpactPathPruningSummary.EdgeExample(
                            ChaReceiverTypeResolver.nodeIdentity(
                                    edge.caller()),
                            ChaReceiverTypeResolver.nodeIdentity(
                                    edge.callee()),
                            outcome.programCounter(),
                            outcome.decision().identifier(),
                            outcome.reason(), outcome.receiverSummary());
            synchronized (examples) {
                examples.put(example.stableKey(), example);
                while (examples.size() > EXAMPLE_LIMIT) {
                    examples.pollLastEntry();
                }
            }
        }

        ImpactPathPruningSummary.ExtensionSummary summary() {
            final List<ImpactPathPruningSummary.EdgeExample> stable;
            synchronized (examples) {
                stable = new ArrayList<>(examples.values());
            }
            return new ImpactPathPruningSummary.ExtensionSummary(
                    extension.identifier(), true,
                    ImpactPathPruningSummary.Status.APPLIED,
                    extension.counters().snapshot(), stable);
        }
    }
}
