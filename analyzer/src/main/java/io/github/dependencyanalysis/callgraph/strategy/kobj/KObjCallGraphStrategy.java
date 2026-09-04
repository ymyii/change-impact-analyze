package io.github.dependencyanalysis.callgraph.strategy.kobj;

import io.github.dependencyanalysis.callgraph.jdk.JdkModelInstallation;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithmStrategy;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphBuildContext;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyRequest;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyCapabilities;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyResult;
import io.github.dependencyanalysis.callgraph.strategy.StrategyModelMetadata;
import io.github.dependencyanalysis.callgraph.strategy.kobj.invokedynamic.KObjInvokeDynamicInstaller;
import io.github.dependencyanalysis.callgraph.strategy.kobj.methodhandle.KObjMethodHandleInstaller;
import io.github.dependencyanalysis.callgraph.strategy.kobj.serviceloader.KObjServiceLoaderInstaller;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;

import java.util.ArrayList;

/** Configurable receiver allocation-string-sensitive strategy. */
public final class KObjCallGraphStrategy
        implements CallGraphAlgorithmStrategy {

    /** Exact allocation sites and constant-specific keys; no smushing. */
    public static final int INSTANCE_POLICY = ZeroXInstanceKeys.ALLOCATIONS
            | ZeroXInstanceKeys.CONSTANT_SPECIFIC;

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.K_OBJ;
    }

    @Override
    public CallGraphStrategyCapabilities capabilities() {
        return CallGraphStrategyCapabilities.propagation();
    }

    @Override
    public CallGraphStrategyResult build(
            final CallGraphBuildContext context,
            final CallGraphStrategyRequest strategyRequest)
            throws Exception {
        if (!(strategyRequest instanceof KObjCallGraphRequest request)) {
            throw new IllegalArgumentException("k-obj request required");
        }
        final AnalysisOptions options = options(context, request);
        final JdkModelInstallation jdkModel = JdkModelInstallation.install(
                request.jdkModel(), options, context.hierarchy());
        final KObjInvokeDynamicInstaller dynamic =
                new KObjInvokeDynamicInstaller();
        dynamic.install(options, context.dynamicModels());
        final SSAPropagationCallGraphBuilder builder =
                new KObjCallGraphBuilder(
                request.depth(), context.hierarchy(), options,
                context.cache(), INSTANCE_POLICY);
        new KObjMethodHandleInstaller().install(
                options, builder);
        final KObjServiceLoaderInstaller serviceLoader =
                new KObjServiceLoaderInstaller(
                        context.serviceLoaderIndex(), context.hierarchy());
        serviceLoader.install(builder);
        request.dependencyBoundary().install(builder);
        final com.ibm.wala.ipa.callgraph.CallGraph graph =
                builder.makeCallGraph(options, context.monitor());
        final ArrayList<ModelLimitation> limitations =
                new ArrayList<>(dynamic.limitations());
        limitations.addAll(serviceLoader.limitations());
        return new CallGraphStrategyResult(graph,
                new StrategyModelMetadata(dynamic.evidence(),
                        limitations,
                        jdkModel.snapshot()));
    }

    private AnalysisOptions options(
            final CallGraphBuildContext context,
            final KObjCallGraphRequest request) {
        final AnalysisOptions result = new AnalysisOptions(
                context.scope(), context.entrypoints());
        result.setReflectionOptions(request.reflectionOptions().walaValue());
        Util.addDefaultSelectors(result, context.hierarchy());
        Util.addDefaultBypassLogic(result, Util.class.getClassLoader(),
                context.hierarchy());
        return result;
    }
}
