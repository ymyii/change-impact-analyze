package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;

/** 1-object-1-call-site WALA MethodHandle extension installation. */
final class OneObjectOneCallSiteMethodHandleInstaller {

    void install(
            final AnalysisOptions options,
            final SSAPropagationCallGraphBuilder builder) {
        MethodHandles.analyzeMethodHandles(options, builder);
    }
}
