package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.impact.ServiceLoaderOverlay;

import java.util.Objects;

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

    /** Entrypoint count. */
    private final int entrypointCount;

    /** Parameter candidate count. */
    private final int parameterCandidateCount;

    /** Conservative ServiceLoader overlay. */
    private ServiceLoaderOverlay serviceLoaderOverlay =
            ServiceLoaderOverlay.empty();

    /**
     * Creates a live module Call Graph session.
     *
     * @param callGraph WALA graph
     * @param cha class hierarchy
     * @param analysisScope WALA scope
     * @param ownershipIndex binary-name ownership
     * @param cache target-side SSA cache
     * @param graphStats build metrics
     * @param entrypointMetrics entrypoint construction metrics
     */
    ModuleCallGraphSession(
            final com.ibm.wala.ipa.callgraph.CallGraph callGraph,
            final IClassHierarchy cha,
            final AnalysisScope analysisScope,
            final ClassOwnershipIndex ownershipIndex,
            final IAnalysisCacheView cache,
            final CallGraphStats graphStats,
            final EntrypointMetrics entrypointMetrics) {
        graph = Objects.requireNonNull(callGraph, "callGraph");
        hierarchy = Objects.requireNonNull(cha, "hierarchy");
        scope = Objects.requireNonNull(analysisScope, "scope");
        ownership = Objects.requireNonNull(ownershipIndex, "ownership");
        analysisCache = Objects.requireNonNull(cache, "analysisCache");
        stats = Objects.requireNonNull(graphStats, "stats");
        entrypointCount = entrypointMetrics.entrypointCount();
        parameterCandidateCount =
                entrypointMetrics.parameterCandidateCount();
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
        return entrypointCount;
    }

    /** @return total entrypoint parameter candidates */
    public int getParameterCandidateCount() {
        return parameterCandidateCount;
    }

    /**
     * Attaches the immutable ServiceLoader overlay before querying.
     *
     * @param overlay conservative overlay
     */
    public void attachServiceLoaderOverlay(
            final ServiceLoaderOverlay overlay) {
        serviceLoaderOverlay = Objects.requireNonNull(overlay, "overlay");
    }

    /** @return conservative ServiceLoader overlay */
    public ServiceLoaderOverlay getServiceLoaderOverlay() {
        return serviceLoaderOverlay;
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
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader)
                ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
    }
}
