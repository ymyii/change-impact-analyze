package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;

import java.util.List;

/** ZeroCFA-specific invokedynamic selector installation and metadata state. */
final class ZeroCfaInvokeDynamicInstaller {

    /** Per-build typed evidence state. */
    private final InvokeDynamicModelState state =
            new InvokeDynamicModelState();

    void install(
            final AnalysisOptions options,
            final InvokeDynamicBootstrapModelRegistry models) {
        options.setSelector(new ZeroCfaInvokeDynamicTargetSelector(
                options.getMethodTargetSelector(), models, state));
    }

    DynamicCallEvidenceIndex evidence() {
        return state.evidenceIndex();
    }

    List<ModelLimitation> limitations() {
        return state.limitations();
    }
}
