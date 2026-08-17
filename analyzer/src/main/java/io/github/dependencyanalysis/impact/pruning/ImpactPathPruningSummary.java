package io.github.dependencyanalysis.impact.pruning;

import java.util.List;
import java.util.Objects;

/**
 * Immutable evidence from the fixed Impact Path pruning registry.
 *
 * @param extensions fixed-order extension summaries
 */
public record ImpactPathPruningSummary(
        List<ExtensionSummary> extensions) {

    /** Stable extension execution state. */
    public enum Status {
        /** The extension was applied to a CHA query. */
        APPLIED("applied"),
        /** The fixed CHA extension was not applicable to this algorithm. */
        NOT_APPLIED_NON_CHA("not applied by non-cha"),
        /** No Impact Path query was executed for the Module. */
        NOT_EXECUTED("not executed");

        /** Stable report label. */
        private final String label;

        Status(final String stableLabel) {
            label = stableLabel;
        }

        /** @return stable report label */
        public String label() {
            return label;
        }
    }

    /**
     * Common and extension-specific counters.
     *
     * @param requests edge evaluation requests
     * @param uniqueEvaluations unique cached evaluations
     * @param cacheHits repeated requests served from cache
     * @param pruned proven-infeasible edges
     * @param feasible proven-feasible edges
     * @param unknown fail-open unknown edges
     * @param notApplicable edges outside the extension gate
     * @param failOpenErrors non-interruption failures retained fail-open
     * @param callsitesChecked callsites inspected
     * @param invokeInstancesChecked invoke instructions inspected
     * @param exactResolutions exact local receiver resolutions
     * @param upperBoundResolutions upper-bound receiver resolutions
     * @param noNormalTargetResolutions null or impossible receiver results
     * @param unknownResolutions unknown local receiver resolutions
     */
    public record Metrics(
            long requests,
            long uniqueEvaluations,
            long cacheHits,
            long pruned,
            long feasible,
            long unknown,
            long notApplicable,
            long failOpenErrors,
            long callsitesChecked,
            long invokeInstancesChecked,
            long exactResolutions,
            long upperBoundResolutions,
            long noNormalTargetResolutions,
            long unknownResolutions) {

        /** @return zero-valued metrics */
        public static Metrics empty() {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0);
        }
    }

    /**
     * One bounded stable caller-to-callee edge decision.
     *
     * @param caller predecessor being considered
     * @param callee current reverse-search node
     * @param programCounter relevant callsite bytecode program counter
     * @param decision stable decision identifier
     * @param reason stable decision reason
     * @param inferredReceiverSummary inferred receiver summary
     */
    public record EdgeExample(
            String caller,
            String callee,
            int programCounter,
            String decision,
            String reason,
            String inferredReceiverSummary) {

        /** Validates immutable example fields. */
        public EdgeExample {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(callee, "callee");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(inferredReceiverSummary,
                    "inferredReceiverSummary");
        }

        /** @return deterministic bounded-example key */
        public String stableKey() {
            return caller + "|" + callee + "|"
                    + programCounter + "|" + decision + "|" + reason + "|"
                    + inferredReceiverSummary;
        }
    }

    /**
     * One fixed extension summary.
     *
     * @param identifier stable extension identifier
     * @param experimental whether the extension is experimental
     * @param status application status
     * @param metrics aggregate counters
     * @param examples bounded stable examples
     */
    public record ExtensionSummary(
            String identifier,
            boolean experimental,
            Status status,
            Metrics metrics,
            List<EdgeExample> examples) {

        /** Validates and freezes the summary. */
        public ExtensionSummary {
            Objects.requireNonNull(identifier, "identifier");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(metrics, "metrics");
            examples = List.copyOf(examples);
        }
    }

    /** Fixed registry identifiers in evaluation order. */
    public static final List<String> FIXED_EXTENSION_IDS = List.of(
            "cha-local-receiver-inference");

    /** Validates fixed ordered summaries. */
    public ImpactPathPruningSummary {
        extensions = List.copyOf(extensions);
        if (!extensions.stream().map(ExtensionSummary::identifier).toList()
                .equals(FIXED_EXTENSION_IDS)) {
            throw new IllegalArgumentException(
                    "Impact Path pruning extensions must use fixed order");
        }
    }

    /** @return summary for a Module without an Impact Path query */
    public static ImpactPathPruningSummary notExecuted() {
        return withStatus(Status.NOT_EXECUTED);
    }

    /** @return summary for an algorithm other than CHA */
    public static ImpactPathPruningSummary notAppliedNonCha() {
        return withStatus(Status.NOT_APPLIED_NON_CHA);
    }

    private static ImpactPathPruningSummary withStatus(final Status status) {
        return new ImpactPathPruningSummary(FIXED_EXTENSION_IDS.stream()
                .map(identifier -> new ExtensionSummary(identifier, true,
                        status, Metrics.empty(), List.of()))
                .toList());
    }

    /** @return total unique edge checks across extensions */
    public long checkedEdges() {
        return extensions.stream().mapToLong(value ->
                value.metrics().uniqueEvaluations()).sum();
    }

    /** @return total proven-infeasible edge decisions */
    public long prunedEdges() {
        return extensions.stream().mapToLong(value ->
                value.metrics().pruned()).sum();
    }

    /** @return total fail-open unknown edge decisions */
    public long unknownEdges() {
        return extensions.stream().mapToLong(value ->
                value.metrics().unknown()).sum();
    }
}
