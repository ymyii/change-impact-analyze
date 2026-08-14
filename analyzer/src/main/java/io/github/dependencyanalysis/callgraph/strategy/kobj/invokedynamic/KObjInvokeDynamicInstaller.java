package io.github.dependencyanalysis.callgraph.strategy.kobj.invokedynamic;

import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapModelRegistry;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicModelState;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;

import java.util.List;

/** k-object-sensitive invokedynamic installation and metadata state. */
public final class KObjInvokeDynamicInstaller {

    /** Per-build typed evidence state. */
    private final InvokeDynamicModelState state =
            new InvokeDynamicModelState();

    /**
     * Installs the k-object dynamic target selector.
     *
     * @param options analysis options
     * @param models bootstrap model registry
     */
    public void install(
            final AnalysisOptions options,
            final InvokeDynamicBootstrapModelRegistry models) {
        options.setSelector(new KObjInvokeDynamicTargetSelector(
                options.getMethodTargetSelector(), models, state));
    }

    /** @return fixed-point dynamic evidence */
    public DynamicCallEvidenceIndex evidence() {
        return state.evidenceIndex();
    }

    /** @return fixed-point model limitations */
    public List<ModelLimitation> limitations() {
        return state.limitations();
    }
}
