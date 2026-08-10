package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;

import java.util.ArrayList;

/** Allocation-sensitive optimized 0-1-CFA strategy. */
final class OptimizedZeroOneCfaCallGraphStrategy
        implements CallGraphAlgorithmStrategy {

    /** Stable optimized ZeroX instance-key policy. */
    static final int INSTANCE_POLICY = ZeroXInstanceKeys.ALLOCATIONS
            | ZeroXInstanceKeys.CONSTANT_SPECIFIC
            | ZeroXInstanceKeys.SMUSH_MANY
            | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS
            | ZeroXInstanceKeys.SMUSH_STRINGS
            | ZeroXInstanceKeys.SMUSH_THROWABLES;

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA;
    }

    @Override
    public CallGraphStrategyResult build(final CallGraphBuildRequest request)
            throws Exception {
        final AnalysisOptions options = options(request);
        final OptimizedInvokeDynamicInstaller dynamic =
                new OptimizedInvokeDynamicInstaller();
        dynamic.install(options, request.dynamicModels());
        final SSAPropagationCallGraphBuilder builder = ZeroXCFABuilder.make(
                Language.JAVA, request.hierarchy(), options,
                request.cache(), null, null, INSTANCE_POLICY);
        new OptimizedMethodHandleInstaller().install(options, builder);
        final OptimizedServiceLoaderInstaller serviceLoader =
                new OptimizedServiceLoaderInstaller(
                        request.serviceLoaderIndex(), request.hierarchy());
        serviceLoader.install(builder);
        request.dependencyBoundary().install(builder);
        final com.ibm.wala.ipa.callgraph.CallGraph graph =
                builder.makeCallGraph(options, request.monitor());
        final ArrayList<ModelLimitation> limitations =
                new ArrayList<>(dynamic.limitations());
        limitations.addAll(serviceLoader.limitations());
        return new CallGraphStrategyResult(graph,
                new StrategyModelMetadata(dynamic.evidence(),
                        limitations, serviceLoader.metadata(graph)));
    }

    private AnalysisOptions options(final CallGraphBuildRequest request) {
        final AnalysisOptions result = new AnalysisOptions(
                request.scope(), request.entrypoints());
        result.setReflectionOptions(request.reflectionOptions().walaValue());
        Util.addDefaultSelectors(result, request.hierarchy());
        Util.addDefaultBypassLogic(result, Util.class.getClassLoader(),
                request.hierarchy());
        return result;
    }
}
