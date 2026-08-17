package io.github.dependencyanalysis.callgraph.strategy.cha;

import java.util.List;
import java.util.Objects;

/**
 * Immutable CHA evidence for the fixed JDK-declared dispatch boundary.
 *
 * @param prunedTargetCount distinct removed dispatch targets
 * @param examples bounded stable examples
 */
public record JdkDeclaredDispatchPruningSummary(
        int prunedTargetCount,
        List<TargetExample> examples) {

    /** Validates non-negative, immutable evidence. */
    public JdkDeclaredDispatchPruningSummary {
        if (prunedTargetCount < 0) {
            throw new IllegalArgumentException("negative pruned target count");
        }
        examples = List.copyOf(examples);
    }

    /** @return empty summary used by algorithms without this CHA rule */
    public static JdkDeclaredDispatchPruningSummary empty() {
        return new JdkDeclaredDispatchPruningSummary(0, List.of());
    }

    /**
     * One bounded stable removed target.
     *
     * @param declaredTarget JDK-declared virtual/interface method
     * @param removedTarget removed non-concrete or non-JDK candidate
     * @param removedOrigin candidate origin
     * @param reason stable removal reason
     */
    public record TargetExample(
            String declaredTarget,
            String removedTarget,
            String removedOrigin,
            String reason) {

        /** Validates immutable example values. */
        public TargetExample {
            Objects.requireNonNull(declaredTarget, "declaredTarget");
            Objects.requireNonNull(removedTarget, "removedTarget");
            Objects.requireNonNull(removedOrigin, "removedOrigin");
            Objects.requireNonNull(reason, "reason");
        }

        /** @return stable bounded-example key */
        public String stableKey() {
            return declaredTarget + "|" + removedTarget + "|"
                    + removedOrigin + "|" + reason;
        }
    }
}
