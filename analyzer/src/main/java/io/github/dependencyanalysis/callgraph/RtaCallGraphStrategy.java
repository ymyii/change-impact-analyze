package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.impl.DefaultContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DefaultSSAInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa
        .DelegatingSSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.rta.BasicRTABuilder;

// Wiki: wiki/features/call-graph-engine.md - Default RTA build strategy
/** Basic RTA strategy without ZeroX points-to emulation. */
final class RtaCallGraphStrategy implements CallGraphAlgorithmStrategy {

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.RTA;
    }

    @Override
    public CallGraphStrategyResult build(final CallGraphBuildRequest request)
            throws Exception {
        final AnalysisOptions options = new AnalysisOptions(
                request.scope(), request.entrypoints());
        options.setReflectionOptions(request.reflectionOptions().walaValue());
        Util.addDefaultSelectors(options, request.hierarchy());
        Util.addDefaultBypassLogic(options, Util.class.getClassLoader(),
                request.hierarchy());
        final JdkModelInstallation jdkModel = JdkModelInstallation.install(
                request.jdkModel(), options, request.hierarchy());
        final RtaInvokeDynamicInstaller dynamic =
                new RtaInvokeDynamicInstaller();
        dynamic.install(options, request.dynamicModels());
        final RtaMethodHandleInstaller methodHandles =
                new RtaMethodHandleInstaller(request.hierarchy());
        methodHandles.installSelector(options);
        final RtaServiceLoaderInstaller serviceLoader =
                new RtaServiceLoaderInstaller(
                        request.serviceLoaderIndex(), request.hierarchy(),
                        new DefaultContextSelector(
                                options, request.hierarchy()));
        final SSAContextInterpreter methodHandleInterpreter =
                methodHandles.interpreter(new DefaultSSAInterpreter(
                        options, request.cache()));
        final SSAContextInterpreter baseInterpreter =
                new DelegatingSSAContextInterpreter(
                        methodHandleInterpreter,
                        serviceLoader.contextInterpreter());
        final ContextSelector contextSelector = request.dependencyBoundary()
                .active() ? request.dependencyBoundary().wrapSelector(
                serviceLoader.contextSelector(), baseInterpreter)
                : serviceLoader.contextSelector();
        final SSAContextInterpreter contextInterpreter =
                request.dependencyBoundary().active()
                        ? request.dependencyBoundary().wrapInterpreter(
                        baseInterpreter) : baseInterpreter;
        final BasicRTABuilder builder = new BasicRTABuilder(
                request.hierarchy(), options, request.cache(),
                contextSelector, contextInterpreter);
        builder.setInstanceKeys(new RtaClassBasedInstanceKeys(
                options, builder.getInstanceKeys()));
        final com.ibm.wala.ipa.callgraph.CallGraph graph =
                builder.makeCallGraph(options, request.monitor());
        final java.util.ArrayList<DynamicCallEvidence> evidence =
                new java.util.ArrayList<>(
                        dynamic.evidence().all());
        evidence.addAll(methodHandles.evidence());
        final java.util.ArrayList<ModelLimitation> limitations =
                new java.util.ArrayList<>(dynamic.limitations());
        limitations.addAll(methodHandles.limitations());
        limitations.addAll(serviceLoader.limitations());
        return new CallGraphStrategyResult(graph,
                new StrategyModelMetadata(
                        new DynamicCallEvidenceIndex(evidence),
                        limitations,
                        serviceLoader.metadata(graph),
                        jdkModel.snapshot()));
    }
}
