package io.github.dependencyanalysis.callgraph.strategy;

import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapModelRegistry;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProtocolIndex;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import java.util.List;
import java.util.Objects;

/**
 * Algorithm-neutral immutable context for one fixed-point build.
 *
 * @param scope WALA analysis scope
 * @param hierarchy resolved class hierarchy
 * @param entrypoints selected entrypoints
 * @param cache WALA analysis cache
 * @param serviceLoaderIndex common ServiceLoader contracts
 * @param dynamicModels common invokedynamic bootstrap models
 * @param monitor build progress and timeout monitor
 */
public record CallGraphBuildContext(
        AnalysisScope scope,
        IClassHierarchy hierarchy,
        List<Entrypoint> entrypoints,
        IAnalysisCacheView cache,
        ServiceLoaderProtocolIndex serviceLoaderIndex,
        InvokeDynamicBootstrapModelRegistry dynamicModels,
        IProgressMonitor monitor) {

    /** Validates and freezes the common context. */
    public CallGraphBuildContext {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(hierarchy, "hierarchy");
        entrypoints = List.copyOf(Objects.requireNonNull(
                entrypoints, "entrypoints"));
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(serviceLoaderIndex, "serviceLoaderIndex");
        Objects.requireNonNull(dynamicModels, "dynamicModels");
        Objects.requireNonNull(monitor, "monitor");
    }
}
