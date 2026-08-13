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
 * @param jdkModel installed JDK Method Model metadata
 * @param capabilities declared strategy behavior
 * @param boundaryOverride algorithm-owned body boundary metadata
 */
record StrategyModelMetadata(
        DynamicCallEvidenceIndex dynamicEvidence,
        List<ModelLimitation> limitations,
        Optional<JdkModelMetadata> jdkModel,
        CallGraphStrategyCapabilities capabilities,
        Optional<DependencyBodyBoundaryMetadata> boundaryOverride) {

    StrategyModelMetadata {
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        limitations = immutable(limitations, "limitations");
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(boundaryOverride, "boundaryOverride");
    }

    StrategyModelMetadata(
            final DynamicCallEvidenceIndex evidence,
            final List<ModelLimitation> modelLimitations) {
        this(evidence, modelLimitations, Optional.empty(),
                CallGraphStrategyCapabilities.propagation(),
                Optional.empty());
    }

    StrategyModelMetadata(
            final DynamicCallEvidenceIndex evidence,
            final List<ModelLimitation> modelLimitations,
            final Optional<JdkModelMetadata> installedJdkModel) {
        this(evidence, modelLimitations, installedJdkModel,
                CallGraphStrategyCapabilities.propagation(),
                Optional.empty());
    }

    private static List<ModelLimitation> immutable(
            final List<ModelLimitation> values,
            final String name) {
        return Objects.requireNonNull(values, name).stream()
                .distinct().sorted().toList();
    }
}
