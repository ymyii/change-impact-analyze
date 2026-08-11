package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.models.jdk.JdkModelMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable strategy model output published after fixed point.
 *
 * @param dynamicEvidence reachable dynamic-call evidence
 * @param limitations typed fixed-point model limitations
 * @param serviceLoader ServiceLoader metadata
 * @param jdkModel installed JDK Method Model metadata
 */
record StrategyModelMetadata(
        DynamicCallEvidenceIndex dynamicEvidence,
        List<ModelLimitation> limitations,
        ServiceLoaderModelMetadata serviceLoader,
        Optional<JdkModelMetadata> jdkModel) {

    StrategyModelMetadata {
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        limitations = immutable(limitations, "limitations");
        Objects.requireNonNull(serviceLoader, "serviceLoader");
        Objects.requireNonNull(jdkModel, "jdkModel");
    }

    StrategyModelMetadata(
            final DynamicCallEvidenceIndex evidence,
            final List<ModelLimitation> modelLimitations,
            final ServiceLoaderModelMetadata loaderMetadata) {
        this(evidence, modelLimitations, loaderMetadata, Optional.empty());
    }

    private static List<ModelLimitation> immutable(
            final List<ModelLimitation> values,
            final String name) {
        return Objects.requireNonNull(values, name).stream()
                .distinct().sorted().toList();
    }
}
