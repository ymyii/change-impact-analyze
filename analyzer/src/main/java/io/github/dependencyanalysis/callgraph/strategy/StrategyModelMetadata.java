package io.github.dependencyanalysis.callgraph.strategy;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundaryMetadata;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.strategy.cha
        .JdkDeclaredDispatchPruningSummary;
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
 * @param jdkDeclaredDispatchPruning fixed CHA JDK dispatch evidence
 * @param boundaryOverride algorithm-owned body boundary metadata
 */
public record StrategyModelMetadata(
        DynamicCallEvidenceIndex dynamicEvidence,
        List<ModelLimitation> limitations,
        Optional<JdkModelMetadata> jdkModel,
        CallGraphStrategyCapabilities capabilities,
        JdkDeclaredDispatchPruningSummary jdkDeclaredDispatchPruning,
        Optional<DependencyBodyBoundaryMetadata> boundaryOverride) {

    /** Validates and freezes all model output. */
    public StrategyModelMetadata {
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        limitations = immutable(limitations, "limitations");
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(jdkDeclaredDispatchPruning,
                "jdkDeclaredDispatchPruning");
        Objects.requireNonNull(boundaryOverride, "boundaryOverride");
    }

    /**
     * Creates propagation metadata without JDK or boundary output.
     *
     * @param evidence dynamic evidence
     * @param modelLimitations typed limitations
     */
    public StrategyModelMetadata(
            final DynamicCallEvidenceIndex evidence,
            final List<ModelLimitation> modelLimitations) {
        this(evidence, modelLimitations, Optional.empty(),
                CallGraphStrategyCapabilities.propagation(),
                JdkDeclaredDispatchPruningSummary.empty(),
                Optional.empty());
    }

    /**
     * Creates propagation metadata with optional JDK model output.
     *
     * @param evidence dynamic evidence
     * @param modelLimitations typed limitations
     * @param installedJdkModel installed JDK model metadata
     */
    public StrategyModelMetadata(
            final DynamicCallEvidenceIndex evidence,
            final List<ModelLimitation> modelLimitations,
            final Optional<JdkModelMetadata> installedJdkModel) {
        this(evidence, modelLimitations, installedJdkModel,
                CallGraphStrategyCapabilities.propagation(),
                JdkDeclaredDispatchPruningSummary.empty(),
                Optional.empty());
    }

    private static List<ModelLimitation> immutable(
            final List<ModelLimitation> values,
            final String name) {
        return Objects.requireNonNull(values, name).stream()
                .distinct().sorted().toList();
    }
}
