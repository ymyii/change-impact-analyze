package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.dependencyanalysis.impact.StructuralScanResult;

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
    private final List<String> modelLimitations;

    /** Stable invokedynamic limitations. */
    private final List<String> dynamicLimitations;

    /** Stable ServiceLoader limitations. */
    private final List<String> serviceLoaderLimitations;

    /** ServiceLoader fixed-point metadata. */
    private final ServiceLoaderFixedPointModel serviceLoaderModel;

    /** Structural metadata indexed before Call Graph construction. */
    private final StructuralScanResult structuralScan;

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
        dynamicEvidence = values.dynamicEvidence();
        serviceLoaderModel = values.serviceLoader();
        structuralScan = values.structuralScan();
        dynamicLimitations = values.dynamicLimitations();
        serviceLoaderLimitations = serviceLoaderModel.limitations();
        final List<String> limitations = new ArrayList<>(
                dynamicLimitations);
        limitations.addAll(serviceLoaderLimitations);
        modelLimitations = limitations.stream().distinct().sorted().toList();
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
        return modelLimitations;
    }

    /** @return true when a fixed-point model reported a limitation */
    public boolean isModelInconclusive() {
        return !modelLimitations.isEmpty();
    }

    /** @return true when invokedynamic modeling was incomplete */
    public boolean hasDynamicModelLimitations() {
        return !dynamicLimitations.isEmpty();
    }

    /** @return true when ServiceLoader modeling was incomplete */
    public boolean hasServiceLoaderLimitations() {
        return !serviceLoaderLimitations.isEmpty();
    }

    /** @return pre-graph immutable structural evidence */
    public StructuralScanResult getStructuralScan() {
        return structuralScan;
    }

    /**
     * Returns metadata when the caller is a synthetic ServiceLoader model.
     *
     * @param caller WALA caller
     * @return synthetic edge metadata, or null
     */
    public SyntheticEdgeMetadata syntheticEdge(final CGNode caller) {
        if (!serviceLoaderModel.models(caller)) {
            return null;
        }
        return new SyntheticEdgeMetadata(EdgeKind.SERVICE_LOADER,
                "SERVICE_LOADER|FIXED_POINT|service="
                        + serviceLoaderModel.serviceLabel(caller));
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
