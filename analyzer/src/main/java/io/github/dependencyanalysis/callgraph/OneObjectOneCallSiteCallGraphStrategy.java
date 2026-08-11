package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjBuilder;

import java.util.ArrayList;

// Wiki: wiki/features/call-graph-engine.md - Fixed-point Installation Order.
/** One receiver allocation string plus one call string strategy. */
final class OneObjectOneCallSiteCallGraphStrategy
        implements CallGraphAlgorithmStrategy {

    /** Exact allocation sites and constant-specific keys; no smushing. */
    static final int INSTANCE_POLICY = ZeroXInstanceKeys.ALLOCATIONS
            | ZeroXInstanceKeys.CONSTANT_SPECIFIC;

    /** Receiver allocation-string depth. */
    private static final int OBJECT_CONTEXT_DEPTH = 1;

    /** Call-string depth. */
    private static final int CALL_SITE_CONTEXT_DEPTH = 1;

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE;
    }

    @Override
    public CallGraphStrategyResult build(final CallGraphBuildRequest request)
            throws Exception {
        final AnalysisOptions options = options(request);
        final JdkModelInstallation jdkModel = JdkModelInstallation.install(
                request.jdkModel(), options, request.hierarchy());
        final OneObjectOneCallSiteInvokeDynamicInstaller dynamic =
                new OneObjectOneCallSiteInvokeDynamicInstaller();
        dynamic.install(options, request.dynamicModels());
        final SSAPropagationCallGraphBuilder builder = new nObjBuilder(
                OBJECT_CONTEXT_DEPTH, request.hierarchy(), options,
                request.cache(), null, null, INSTANCE_POLICY);
        builder.setContextSelector(new nCFAContextSelector(
                CALL_SITE_CONTEXT_DEPTH, builder.getContextSelector()));
        new OneObjectOneCallSiteMethodHandleInstaller().install(
                options, builder);
        final OneObjectOneCallSiteServiceLoaderInstaller serviceLoader =
                new OneObjectOneCallSiteServiceLoaderInstaller(
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
