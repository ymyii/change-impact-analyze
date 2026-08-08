package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;

/** ZeroCFA WALA MethodHandle extension installation. */
final class ZeroCfaMethodHandleInstaller {

    void install(
            final AnalysisOptions options,
            final SSAPropagationCallGraphBuilder builder) {
        MethodHandles.analyzeMethodHandles(options, builder);
    }
}
