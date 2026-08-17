package io.github.dependencyanalysis.impact.pruning.cha;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;

import java.util.concurrent.atomic.LongAdder;

/** Internal contract implemented only by the fixed CHA extension registry. */
interface ImpactPathPruningExtension {

    /** @return stable built-in extension identifier */
    String identifier();

    /**
     * @param edge caller-to-callee edge
     * @return immutable evaluation cache key
     */
    Object cacheKey(PruningEdge edge);

    /**
     * @param edge caller-to-callee edge
     * @return fail-open pruning decision
     */
    PruningEvaluation evaluate(PruningEdge edge);

    /** @return thread-safe extension counters */
    ExtensionCounters counters();
}

/**
 * One caller-to-callee reverse traversal edge.
 *
 * @param caller predecessor being considered
 * @param callee current reverse-search node
 */
record PruningEdge(CGNode caller, CGNode callee) {
}

/** Extension decision categories. */
enum PruningDecision {
    /** Transition is proven impossible. */
    PROVEN_INFEASIBLE("proven-infeasible"),
    /** Extension proves that the transition can reach the candidate. */
    FEASIBLE("feasible"),
    /** Evidence is incomplete, so traversal remains fail-open. */
    UNKNOWN("unknown"),
    /** Transition does not match the extension's inference pattern. */
    NOT_APPLICABLE("not-applicable");

    /** Stable diagnostics identifier. */
    private final String identifier;

    PruningDecision(final String stableIdentifier) {
        identifier = stableIdentifier;
    }

    String identifier() {
        return identifier;
    }
}

/**
 * Stable extension decision and optional callsite identity.
 *
 * @param decision pruning decision
 * @param programCounter relevant bytecode program counter
 * @param reason stable reason identifier
 * @param receiverSummary inferred receiver summary
 */
record PruningEvaluation(
        PruningDecision decision,
        int programCounter,
        String reason,
        String receiverSummary) {

    static PruningEvaluation pruned(
            final int pc,
            final String reason,
            final String receiver) {
        return new PruningEvaluation(PruningDecision.PROVEN_INFEASIBLE,
                pc, reason, receiver);
    }

    static PruningEvaluation feasible(
            final int pc,
            final String reason,
            final String receiver) {
        return new PruningEvaluation(PruningDecision.FEASIBLE,
                pc, reason, receiver);
    }

    static PruningEvaluation unknown(
            final int pc,
            final String reason,
            final String receiver) {
        return new PruningEvaluation(PruningDecision.UNKNOWN,
                pc, reason, receiver);
    }

    static PruningEvaluation notApplicable(final String reason) {
        return new PruningEvaluation(PruningDecision.NOT_APPLICABLE,
                -1, reason, "unknown");
    }
}

/** Thread-safe common and extension-specific counters. */
final class ExtensionCounters {
    /** Evaluation request count. */
    private final LongAdder requests = new LongAdder();
    /** Unique evaluation count. */
    private final LongAdder uniqueEvaluations = new LongAdder();
    /** Cached evaluation count. */
    private final LongAdder cacheHits = new LongAdder();
    /** Proven-infeasible count. */
    private final LongAdder pruned = new LongAdder();
    /** Proven-feasible count. */
    private final LongAdder feasible = new LongAdder();
    /** Unknown decision count. */
    private final LongAdder unknown = new LongAdder();
    /** Not-applicable decision count. */
    private final LongAdder notApplicable = new LongAdder();
    /** Fail-open exception count. */
    private final LongAdder failOpenErrors = new LongAdder();
    /** Checked callsite count. */
    private final LongAdder callsitesChecked = new LongAdder();
    /** Checked invoke instruction count. */
    private final LongAdder invokeInstancesChecked = new LongAdder();
    /** Exact local resolution count. */
    private final LongAdder exactResolutions = new LongAdder();
    /** Upper-bound local resolution count. */
    private final LongAdder upperBoundResolutions = new LongAdder();
    /** No-normal-target resolution count. */
    private final LongAdder noNormalTargetResolutions = new LongAdder();
    /** Unknown local resolution count. */
    private final LongAdder unknownResolutions = new LongAdder();

    void request() {
        requests.increment();
    }

    void uniqueEvaluation() {
        uniqueEvaluations.increment();
    }

    void cacheHit() {
        cacheHits.increment();
    }

    void failOpenError() {
        failOpenErrors.increment();
    }

    void callsiteChecked() {
        callsitesChecked.increment();
    }

    void invokeInstanceChecked() {
        invokeInstancesChecked.increment();
    }

    void record(final PruningDecision decision) {
        switch (decision) {
            case PROVEN_INFEASIBLE -> pruned.increment();
            case FEASIBLE -> feasible.increment();
            case UNKNOWN -> unknown.increment();
            case NOT_APPLICABLE -> notApplicable.increment();
            default -> throw new IllegalStateException(
                    "Unhandled pruning decision: " + decision);
        }
    }

    void receiver(final ChaReceiverTypeResolver.Resolution resolution) {
        switch (resolution.kind()) {
            case EXACT -> exactResolutions.increment();
            case UPPER_BOUND -> upperBoundResolutions.increment();
            case NO_NORMAL_TARGET -> noNormalTargetResolutions.increment();
            case UNKNOWN -> unknownResolutions.increment();
            default -> throw new IllegalStateException(
                    "Unhandled receiver kind: " + resolution.kind());
        }
    }

    ImpactPathPruningSummary.Metrics snapshot() {
        return new ImpactPathPruningSummary.Metrics(
                requests.sum(), uniqueEvaluations.sum(), cacheHits.sum(),
                pruned.sum(), feasible.sum(), unknown.sum(),
                notApplicable.sum(), failOpenErrors.sum(),
                callsitesChecked.sum(), invokeInstancesChecked.sum(),
                exactResolutions.sum(), upperBoundResolutions.sum(),
                noNormalTargetResolutions.sum(), unknownResolutions.sum());
    }
}
