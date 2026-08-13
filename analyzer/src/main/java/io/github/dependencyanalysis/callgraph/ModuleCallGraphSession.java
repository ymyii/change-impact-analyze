package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.dependencyanalysis.impact.StructuralReferenceIndex;
import io.github.dependencyanalysis.impact.ChangePointEvidenceIndex;
import io.github.dependencyanalysis.impact.ModuleAnalysisReason;
import io.github.dependencyanalysis.impact.CoverageLimitation;
import io.github.dependencyanalysis.models.jdk.JdkModelMetadata;

/** Live per-module WALA graph and its ownership/metric context. */
public final class ModuleCallGraphSession {

    /** WALA Call Graph. */
    private final com.ibm.wala.ipa.callgraph.CallGraph graph;

    /** Module class hierarchy. */
    private final IClassHierarchy hierarchy;

    /** Module analysis scope. */
    private final AnalysisScope scope;

    /** Binary-name origin resolver. */
    private final ClassOwnershipIndex ownership;

    /** Target-side SSA cache used by deferred equivalence. */
    private final IAnalysisCacheView analysisCache;

    /** Build metrics. */
    private final CallGraphStats stats;

    /** Entrypoint selection metrics. */
    private final EntrypointSelectionMetrics entrypointMetrics;

    /** Reachable invokedynamic evidence captured during fixed point. */
    private final DynamicCallEvidenceIndex dynamicEvidence;

    /** Stable fixed-point model limitations. */
    private final List<ModelLimitation> modelLimitations;

    /** Immutable strategy capability declaration. */
    private final CallGraphStrategyCapabilities strategyCapabilities;

    /** Installed JDK Method Model metadata. */
    private final Optional<JdkModelMetadata> jdkModelMetadata;

    /** External dependency body-boundary evidence and node counts. */
    private final DependencyBodyBoundaryMetadata dependencyBoundary;

    /** Structural metadata indexed before Call Graph construction. */
    private final StructuralReferenceIndex structuralReferences;

    /** Frozen terminal evidence collected after graph completion. */
    private final ChangePointEvidenceIndex changePointEvidence;

    /** Optional read-only benchmark topology capture. */
    private final CallGraphTopologySnapshot topology;

    /**
     * Creates a live module Call Graph session.
     *
     * @param callGraph WALA graph
     * @param cha class hierarchy
     * @param analysisScope WALA scope
     * @param ownershipIndex binary-name ownership
     * @param cache target-side SSA cache
     * @param metadata graph metrics and fixed-point model output
     */
    ModuleCallGraphSession(
            final com.ibm.wala.ipa.callgraph.CallGraph callGraph,
            final IClassHierarchy cha,
            final AnalysisScope analysisScope,
            final ClassOwnershipIndex ownershipIndex,
            final IAnalysisCacheView cache,
            final ModuleCallGraphMetadata metadata) {
        graph = Objects.requireNonNull(callGraph, "callGraph");
        hierarchy = Objects.requireNonNull(cha, "hierarchy");
        scope = Objects.requireNonNull(analysisScope, "scope");
        ownership = Objects.requireNonNull(ownershipIndex, "ownership");
        analysisCache = Objects.requireNonNull(cache, "analysisCache");
        final ModuleCallGraphMetadata values = Objects.requireNonNull(
                metadata, "metadata");
        stats = values.stats();
        entrypointMetrics = values.entrypoints();
        final StrategyModelMetadata strategy = values.strategyModels();
        dynamicEvidence = strategy.dynamicEvidence();
        jdkModelMetadata = strategy.jdkModel();
        strategyCapabilities = strategy.capabilities();
        dependencyBoundary = values.dependencyBoundary();
        structuralReferences = values.structuralReferences();
        changePointEvidence = values.changePointEvidence();
        topology = values.topology();
        modelLimitations = strategy.limitations();
    }

    /** @return live WALA Call Graph */
    public com.ibm.wala.ipa.callgraph.CallGraph getGraph() {
        return graph;
    }

    /** @return module class hierarchy */
    public IClassHierarchy getHierarchy() {
        return hierarchy;
    }

    /** @return module analysis scope */
    public AnalysisScope getScope() {
        return scope;
    }

    /** @return binary-name ownership index */
    public ClassOwnershipIndex getOwnership() {
        return ownership;
    }

    /** @return deterministic conflicting duplicate class resolutions */
    public List<DuplicateClassResolution> getDuplicateClassResolutions() {
        return ownership.duplicateClassResolutions();
    }

    /** @return target-side SSA cache */
    public IAnalysisCacheView getAnalysisCache() {
        return analysisCache;
    }

    /** @return Call Graph metrics */
    public CallGraphStats getStats() {
        return stats;
    }

    /** @return entrypoint count */
    public int getEntrypointCount() {
        return entrypointMetrics.entrypointCount();
    }

    /** @return total entrypoint parameter candidates */
    public int getParameterCandidateCount() {
        return entrypointMetrics.parameterCandidateCount();
    }

    /** @return selected PROJECT class count */
    public int getSelectedEntrypointClassCount() {
        return entrypointMetrics.selectedClassCount();
    }

    /** @return complete entrypoint selection metrics */
    public EntrypointSelectionMetrics getEntrypointMetrics() {
        return entrypointMetrics;
    }

    /** @return reachable invokedynamic evidence */
    public DynamicCallEvidenceIndex getDynamicEvidence() {
        return dynamicEvidence;
    }

    /** @return stable fixed-point model limitations */
    public List<String> getModelLimitations() {
        return allCoverageLimitations().stream()
                .map(ModelLimitation::summary).toList();
    }

    /** @return typed immutable fixed-point coverage limitations */
    public List<ModelLimitation> getCoverageLimitations() {
        return allCoverageLimitations();
    }

    /** @return internal installed JDK Method Model metadata */
    Optional<JdkModelMetadata> jdkModelMetadata() {
        return jdkModelMetadata;
    }

    /** @return dependency body boundary output */
    public DependencyBodyBoundaryMetadata getDependencyBoundary() {
        return dependencyBoundary;
    }

    /** @return typed dependency boundary limitations */
    public List<CoverageLimitation> getDependencyBoundaryLimitations() {
        final java.util.ArrayList<CoverageLimitation> result =
                new java.util.ArrayList<>();
        result.addAll(dependencyBoundary.dangerousTransfers());
        result.addAll(dependencyBoundary.factories());
        result.addAll(dependencyBoundary.bodyBoundaryHits());
        return List.copyOf(result);
    }

    /** @return true when a fixed-point model reported a limitation */
    public boolean isModelInconclusive() {
        return !allCoverageLimitations().isEmpty();
    }

    /** @return true when invokedynamic modeling was incomplete */
    public boolean hasDynamicModelLimitations() {
        return hasReason(
                ModuleAnalysisReason.INCONCLUSIVE_INVOKEDYNAMIC_MODEL);
    }

    /** @return true when MethodHandle local modeling was incomplete */
    public boolean hasMethodHandleLimitations() {
        return hasReason(
                ModuleAnalysisReason.INCONCLUSIVE_METHOD_HANDLE_MODEL);
    }

    /** @return true when ServiceLoader modeling was incomplete */
    public boolean hasServiceLoaderLimitations() {
        return hasReason(ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER);
    }

    private boolean hasReason(final ModuleAnalysisReason reason) {
        return allCoverageLimitations().stream()
                .anyMatch(value -> value.reason() == reason);
    }

    private List<ModelLimitation> allCoverageLimitations() {
        final java.util.LinkedHashSet<ModelLimitation> result =
                new java.util.LinkedHashSet<>(modelLimitations);
        changePointEvidence.limitations().stream()
                .filter(ModelLimitation.class::isInstance)
                .map(ModelLimitation.class::cast)
                .forEach(result::add);
        return result.stream().sorted().toList();
    }

    /** @return pre-graph immutable structural evidence */
    public StructuralReferenceIndex getStructuralReferences() {
        return structuralReferences;
    }

    /** @return frozen complete ChangePoint evidence index */
    public ChangePointEvidenceIndex getChangePointEvidence() {
        return changePointEvidence;
    }

    /** @return immutable strategy capability declaration */
    public CallGraphStrategyCapabilities getStrategyCapabilities() {
        return strategyCapabilities;
    }

    /** @return topology capture when explicitly enabled */
    public Optional<CallGraphTopologySnapshot> getTopology() {
        return Optional.ofNullable(topology);
    }

    /**
     * Resolves origin using ownership first and WALA loader identity as the
     * fallback, distinguishing JDK code from WALA-created synthetic code.
     *
     * @param type WALA declaring class
     * @return indexed, JDK, or synthetic origin
     */
    public CodeOrigin originOf(final IClass type) {
        final ClassOwnership value = ownership.ownershipOf(
                type.getName().toString());
        if (value != null) {
            return value.getOrigin();
        }
        if (type instanceof SyntheticClass) {
            return CodeOrigin.SYNTHETIC;
        }
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader)
                ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
    }
}
