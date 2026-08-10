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
 * @param serviceLoaderIndex immutable validated ServiceLoader facts
 * @param dynamicModels invokedynamic model registry
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyBoundary external method-body boundary model
 * @param monitor cooperative fixed-point monitor
 */
record CallGraphBuildRequest(
        AnalysisScope scope,
        IClassHierarchy hierarchy,
        List<Entrypoint> entrypoints,
        IAnalysisCacheView cache,
        ServiceLoaderProtocolIndex serviceLoaderIndex,
        InvokeDynamicBootstrapModelRegistry dynamicModels,
        WalaReflectionOptions reflectionOptions,
        DependencyBodyBoundary dependencyBoundary,
        IProgressMonitor monitor) {

    CallGraphBuildRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(hierarchy, "hierarchy");
        entrypoints = List.copyOf(Objects.requireNonNull(
                entrypoints, "entrypoints"));
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(serviceLoaderIndex, "serviceLoaderIndex");
        Objects.requireNonNull(dynamicModels, "dynamicModels");
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
        Objects.requireNonNull(monitor, "monitor");
    }
}
