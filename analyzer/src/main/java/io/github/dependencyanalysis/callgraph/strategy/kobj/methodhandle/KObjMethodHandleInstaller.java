package io.github.dependencyanalysis.callgraph.strategy.kobj.methodhandle;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;

/** k-object-sensitive WALA MethodHandle extension installation. */
public final class KObjMethodHandleInstaller {

    /**
     * Installs WALA MethodHandle analysis.
     *
     * @param options analysis options
     * @param builder propagation builder
     */
    public void install(
            final AnalysisOptions options,
            final SSAPropagationCallGraphBuilder builder) {
        MethodHandles.analyzeMethodHandles(options, builder);
    }
}
