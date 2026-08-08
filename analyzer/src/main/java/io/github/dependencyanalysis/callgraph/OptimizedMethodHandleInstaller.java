package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;

/** Optimized 0-1-CFA WALA MethodHandle extension installation. */
final class OptimizedMethodHandleInstaller {

    void install(
            final AnalysisOptions options,
            final SSAPropagationCallGraphBuilder builder) {
        MethodHandles.analyzeMethodHandles(options, builder);
    }
}
