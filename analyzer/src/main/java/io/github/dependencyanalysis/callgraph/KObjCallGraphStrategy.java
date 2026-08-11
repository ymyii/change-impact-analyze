package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;

import java.util.ArrayList;

// Wiki: wiki/features/call-graph-engine.md - Fixed-point Installation Order.
/** Configurable receiver allocation-string-sensitive strategy. */
final class KObjCallGraphStrategy
        implements CallGraphAlgorithmStrategy {

    /** Exact allocation sites and constant-specific keys; no smushing. */
    static final int INSTANCE_POLICY = ZeroXInstanceKeys.ALLOCATIONS
            | ZeroXInstanceKeys.CONSTANT_SPECIFIC;

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.K_OBJ;
    }

    @Override
    public CallGraphStrategyResult build(final CallGraphBuildRequest request)
            throws Exception {
        final AnalysisOptions options = options(request);
        final JdkModelInstallation jdkModel = JdkModelInstallation.install(
                request.jdkModel(), options, request.hierarchy());
        final KObjInvokeDynamicInstaller dynamic =
                new KObjInvokeDynamicInstaller();
        dynamic.install(options, request.dynamicModels());
        final SSAPropagationCallGraphBuilder builder =
                new KObjCallGraphBuilder(
                request.kObjDepth(), request.hierarchy(), options,
                request.cache(), INSTANCE_POLICY);
        new KObjMethodHandleInstaller().install(
                options, builder);
        final KObjServiceLoaderInstaller serviceLoader =
                new KObjServiceLoaderInstaller(
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
                        limitations, serviceLoader.metadata(graph),
                        jdkModel.snapshot()));
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
