package io.github.dependencyanalysis.impact.refinement.cha;

import java.util.List;
import java.util.Objects;

/** Immutable query-time CHA local-receiver refinement evidence. */
public final class ChaLocalReceiverRefinementSummary {

    /** Effective application state. */
    public enum Status {
        /** Algorithm was selected and applied. */
        APPLIED("applied"),

        /** Algorithm was not selected. */
        NOT_SELECTED("not selected"),

        /** Algorithm was selected for a non-CHA graph. */
        NOT_APPLIED_NON_CHA("not applied by non-cha");

        /** Stable report text. */
        private final String label;

        Status(final String stableLabel) {
            label = stableLabel;
        }

        /** @return stable report text */
        public String label() {
            return label;
        }
    }

    /**
     * Aggregate counters for one Module query.
     *
     * @param predecessorEdgeRequests edge validation requests
     * @param uniqueEvaluatedEdges unique edges evaluated
     * @param cacheHits repeated edge requests
     * @param callsitesChecked dynamic callsites inspected
     * @param invokeInstancesChecked invoke instructions inspected
     * @param prunedEdges edges proven infeasible
     * @param retainedFeasibleEdges edges proven able to reach the callee
     * @param retainedUnknownEdges edges retained because proof was incomplete
     * @param notApplicableEdges edges outside the supported inference gate
     * @param exactResolutions exact receiver resolutions
     * @param upperBoundResolutions upper-bound receiver resolutions
     * @param noNormalTargetResolutions null or impossible-cast resolutions
     * @param unknownResolutions unknown receiver resolutions
     */
    public record Metrics(
            long predecessorEdgeRequests,
            long uniqueEvaluatedEdges,
            long cacheHits,
            long callsitesChecked,
            long invokeInstancesChecked,
            long prunedEdges,
            long retainedFeasibleEdges,
            long retainedUnknownEdges,
            long notApplicableEdges,
            long exactResolutions,
            long upperBoundResolutions,
            long noNormalTargetResolutions,
            long unknownResolutions) {

        /** @return zero-valued metrics */
        public static Metrics empty() {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0);
        }
    }

    /**
     * One stable bounded edge decision example.
     *
     * @param caller stable caller node identity
     * @param callee stable callee node identity
     * @param programCounter callsite bytecode program counter
     * @param invocationKind invoke kind
     * @param decision stable decision identifier
     * @param reason decision reason
     * @param receiverSummary receiver inference summary
     */
    public record EdgeExample(
            String caller,
            String callee,
            int programCounter,
            String invocationKind,
            String decision,
            String reason,
            String receiverSummary) {

        /** Validates immutable example fields. */
        public EdgeExample {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(callee, "callee");
            Objects.requireNonNull(invocationKind, "invocationKind");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(receiverSummary, "receiverSummary");
        }

        /** @return deterministic bounded-example key */
        public String stableKey() {
            return caller + "|" + callee + "|" + programCounter + "|"
                    + invocationKind + "|" + decision + "|" + reason
                    + "|" + receiverSummary;
        }
    }

    /** Application state. */
    private final Status status;

    /** Aggregate metrics. */
    private final Metrics metrics;

    /** Bounded deterministic examples. */
    private final List<EdgeExample> examples;

    /**
     * Creates an immutable summary.
     *
     * @param applicationStatus application state
     * @param aggregateMetrics aggregate metrics
     * @param edgeExamples bounded stable examples
     */
    public ChaLocalReceiverRefinementSummary(
            final Status applicationStatus,
            final Metrics aggregateMetrics,
            final List<EdgeExample> edgeExamples) {
        status = Objects.requireNonNull(applicationStatus,
                "applicationStatus");
        metrics = Objects.requireNonNull(aggregateMetrics,
                "aggregateMetrics");
        examples = List.copyOf(edgeExamples);
    }

    /** @return a not-selected summary */
    public static ChaLocalReceiverRefinementSummary notSelected() {
        return new ChaLocalReceiverRefinementSummary(
                Status.NOT_SELECTED, Metrics.empty(), List.of());
    }

    /** @return application state */
    public Status status() {
        return status;
    }

    /** @return aggregate metrics */
    public Metrics metrics() {
        return metrics;
    }

    /** @return bounded deterministic examples */
    public List<EdgeExample> examples() {
        return examples;
    }
}
