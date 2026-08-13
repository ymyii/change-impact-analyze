package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import java.util.List;
import java.util.Objects;

/**
 * Immutable input for one algorithm-specific fixed-point build.
 *
 * @param scope target analysis scope
 * @param hierarchy target class hierarchy
 * @param entrypoints PROJECT entrypoints
 * @param cache target SSA cache
 * @param ownership target binary-name ownership
 * @param chaDispatchTargets CHA Diff-directed dispatch target policy
 * @param serviceLoaderIndex immutable validated ServiceLoader facts
 * @param dynamicModels invokedynamic model registry
 * @param jdkModel command-wide JDK Method Model selection
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyBoundary external method-body boundary model
 * @param kObjDepth receiver allocation-string depth for k-obj
 * @param monitor cooperative fixed-point monitor
 */
record CallGraphBuildRequest(
        AnalysisScope scope,
        IClassHierarchy hierarchy,
        List<Entrypoint> entrypoints,
        IAnalysisCacheView cache,
        ClassOwnershipIndex ownership,
        ChaDispatchTargetPolicy chaDispatchTargets,
        ServiceLoaderProtocolIndex serviceLoaderIndex,
        InvokeDynamicBootstrapModelRegistry dynamicModels,
        JdkModelSelection jdkModel,
        WalaReflectionOptions reflectionOptions,
        DependencyBodyBoundary dependencyBoundary,
        int kObjDepth,
        IProgressMonitor monitor) {

    CallGraphBuildRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(hierarchy, "hierarchy");
        entrypoints = List.copyOf(Objects.requireNonNull(
                entrypoints, "entrypoints"));
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(chaDispatchTargets, "chaDispatchTargets");
        Objects.requireNonNull(serviceLoaderIndex, "serviceLoaderIndex");
        Objects.requireNonNull(dynamicModels, "dynamicModels");
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
        kObjDepth = CallGraphAlgorithm.requireValidKObjDepth(kObjDepth);
        Objects.requireNonNull(monitor, "monitor");
    }

    /**
     * Compatibility constructor using the default JDK model.
     *
     * @param analysisScope target analysis scope
     * @param classHierarchy target class hierarchy
     * @param projectEntrypoints PROJECT entrypoints
     * @param analysisCache target SSA cache
     * @param services immutable validated ServiceLoader facts
     * @param invokedynamicModels invokedynamic model registry
     * @param reflection command-wide WALA ReflectionOptions
     * @param boundary external method-body boundary model
     * @param progressMonitor cooperative fixed-point monitor
     */
    CallGraphBuildRequest(
            final AnalysisScope analysisScope,
            final IClassHierarchy classHierarchy,
            final List<Entrypoint> projectEntrypoints,
            final IAnalysisCacheView analysisCache,
            final ServiceLoaderProtocolIndex services,
            final InvokeDynamicBootstrapModelRegistry invokedynamicModels,
            final WalaReflectionOptions reflection,
            final DependencyBodyBoundary boundary,
            final IProgressMonitor progressMonitor) {
        this(analysisScope, classHierarchy, projectEntrypoints, analysisCache,
                new ClassOwnershipIndex(),
                ChaDispatchTargetPolicy.disabled(), services,
                invokedynamicModels,
                JdkModelSelection.defaultSelection(), reflection, boundary,
                CallGraphAlgorithm.defaultKObjDepth(),
                progressMonitor);
    }
}
