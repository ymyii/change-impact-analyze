package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import java.util.List;

/** RTA-local MethodHandle selector/interpreter installation. */
final class RtaMethodHandleInstaller {

    /** Local fact resolver shared only within this build. */
    private final RtaMethodHandleFactResolver resolver =
            new RtaMethodHandleFactResolver();

    /** Per-build typed evidence state. */
    private final RtaMethodHandleModelState state =
            new RtaMethodHandleModelState();

    /** Per-build stable bridge summary cache. */
    private final RtaMethodHandleSummaryFactory summaries;

    RtaMethodHandleInstaller(final IClassHierarchy hierarchy) {
        summaries = new RtaMethodHandleSummaryFactory(hierarchy);
    }

    void installSelector(final AnalysisOptions options) {
        options.setSelector(new RtaMethodHandleTargetSelector(
                options.getMethodTargetSelector(), resolver, state,
                summaries));
    }

    SSAContextInterpreter interpreter(
            final SSAContextInterpreter base) {
        return new RtaMethodHandleContextInterpreter(
                base, resolver, summaries, state);
    }

    List<DynamicCallEvidence> evidence() {
        return state.evidence();
    }

    List<ModelLimitation> limitations() {
        return state.limitations();
    }
}
