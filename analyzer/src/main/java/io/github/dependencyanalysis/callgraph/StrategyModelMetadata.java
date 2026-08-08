package io.github.dependencyanalysis.callgraph;

import java.util.List;
import java.util.Objects;

/**
 * Immutable strategy model output published after fixed point.
 *
 * @param dynamicEvidence reachable dynamic-call evidence
 * @param limitations typed fixed-point model limitations
 * @param serviceLoader ServiceLoader metadata
 */
record StrategyModelMetadata(
        DynamicCallEvidenceIndex dynamicEvidence,
        List<ModelLimitation> limitations,
        ServiceLoaderModelMetadata serviceLoader) {

    StrategyModelMetadata {
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        limitations = immutable(limitations, "limitations");
        Objects.requireNonNull(serviceLoader, "serviceLoader");
    }

    private static List<ModelLimitation> immutable(
            final List<ModelLimitation> values,
            final String name) {
        return Objects.requireNonNull(values, name).stream()
                .distinct().sorted().toList();
    }
}
