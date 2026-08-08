package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;

import java.util.List;

/** Optimized 0-1-CFA invokedynamic installation and metadata state. */
final class OptimizedInvokeDynamicInstaller {

    /** Per-build typed evidence state. */
    private final InvokeDynamicModelState state =
            new InvokeDynamicModelState();

    void install(
            final AnalysisOptions options,
            final InvokeDynamicBootstrapModelRegistry models) {
        options.setSelector(new OptimizedInvokeDynamicTargetSelector(
                options.getMethodTargetSelector(), models, state));
    }

    DynamicCallEvidenceIndex evidence() {
        return state.evidenceIndex();
    }

    List<ModelLimitation> limitations() {
        return state.limitations();
    }
}
