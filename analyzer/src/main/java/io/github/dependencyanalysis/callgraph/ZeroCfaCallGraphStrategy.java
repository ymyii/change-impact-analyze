package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;

import java.util.ArrayList;

/** Class-based ZeroCFA strategy with constant-specific identities. */
final class ZeroCfaCallGraphStrategy implements CallGraphAlgorithmStrategy {

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.ZERO_CFA;
    }

    @Override
    public CallGraphStrategyResult build(final CallGraphBuildRequest request)
            throws Exception {
        final AnalysisOptions options = options(request);
        final ZeroCfaInvokeDynamicInstaller dynamic =
                new ZeroCfaInvokeDynamicInstaller();
        dynamic.install(options, request.dynamicModels());
        final SSAPropagationCallGraphBuilder builder = ZeroXCFABuilder.make(
                Language.JAVA, request.hierarchy(), options,
                request.cache(), null, null,
                ZeroXInstanceKeys.CONSTANT_SPECIFIC);
        new ZeroCfaMethodHandleInstaller().install(options, builder);
        final ZeroCfaServiceLoaderInstaller serviceLoader =
                new ZeroCfaServiceLoaderInstaller(
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
