package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;

import java.util.List;

/** 1-object-1-call-site invokedynamic installation and metadata state. */
final class OneObjectOneCallSiteInvokeDynamicInstaller {

    /** Per-build typed evidence state. */
    private final InvokeDynamicModelState state =
            new InvokeDynamicModelState();

    void install(
            final AnalysisOptions options,
            final InvokeDynamicBootstrapModelRegistry models) {
        options.setSelector(new OneObjectOneCallSiteInvokeDynamicTargetSelector(
                options.getMethodTargetSelector(), models, state));
    }

    DynamicCallEvidenceIndex evidence() {
        return state.evidenceIndex();
    }

    List<ModelLimitation> limitations() {
        return state.limitations();
    }
}
