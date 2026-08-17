package io.github.dependencyanalysis.impact.pruning.cha;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

import java.util.List;

/** Proves infeasible CHA edges from receiver facts in the caller IR. */
final class ChaLocalReceiverInferenceExtension
        implements ImpactPathPruningExtension {

    /** Stable fixed-registry identifier. */
    static final String ID = "cha-local-receiver-inference";

    /** Shared CHA receiver resolver. */
    private final ChaReceiverTypeResolver resolver;

    /** Extension evidence counters. */
    private final ExtensionCounters counters = new ExtensionCounters();

    ChaLocalReceiverInferenceExtension(
            final ChaReceiverTypeResolver receiverResolver) {
        resolver = receiverResolver;
    }

    @Override
    public String identifier() {
        return ID;
    }

    @Override
    public Object cacheKey(final PruningEdge edge) {
        return new EdgeKey(edge.caller(), edge.callee());
    }

    @Override
    public PruningEvaluation evaluate(final PruningEdge edge) {
        final CGNode caller = edge.caller();
        final CGNode callee = edge.callee();
        if (resolver.facts(caller).ir() == null) {
            return PruningEvaluation.notApplicable("missing-ir");
        }
        final List<CallSiteReference> sites = resolver.sites(caller, callee);
        if (sites.isEmpty()) {
            return PruningEvaluation.unknown(
                    -1, "missing-callsite", "unknown");
        }
        int representativePc = -1;
        String representativeReceiver = "unknown";
        for (CallSiteReference site : sites) {
            if (!site.isDispatch()) {
                return PruningEvaluation.notApplicable("fixed-dispatch");
            }
            if (resolver.session().getGraph()
                    .getNumberOfTargets(caller, site) <= 1) {
                return PruningEvaluation.notApplicable(
                        "single-original-target");
            }
            counters.callsiteChecked();
            final SSAAbstractInvokeInstruction[] invokes =
                    resolver.invokes(caller, site);
            if (invokes.length == 0) {
                return PruningEvaluation.unknown(site.getProgramCounter(),
                        "missing-invoke-instance", "unknown");
            }
            for (SSAAbstractInvokeInstruction invoke : invokes) {
                counters.invokeInstanceChecked();
                if (!invoke.isDispatch()) {
                    return PruningEvaluation.notApplicable(
                            "fixed-invoke-instance");
                }
                final ChaReceiverTypeResolver.Resolution receiver =
                        resolver.receiver(caller, invoke.getReceiver());
                counters.receiver(receiver);
                representativePc = site.getProgramCounter();
                representativeReceiver = receiver.summary();
                final ChaReceiverTypeResolver.DispatchResult dispatch =
                        resolver.dispatch(receiver,
                                invoke.getDeclaredTarget(), callee);
                switch (dispatch.reachability()) {
                    case REACHES -> {
                        return PruningEvaluation.feasible(
                                representativePc, dispatch.reason(),
                                representativeReceiver);
                    }
                    case UNKNOWN -> {
                        return PruningEvaluation.unknown(
                                representativePc, dispatch.reason(),
                                representativeReceiver);
                    }
                    case EXCLUDES -> {
                        // Every associated invoke must exclude the callee.
                    }
                    default -> throw new IllegalStateException(
                            "Unhandled dispatch result: "
                                    + dispatch.reachability());
                }
            }
        }
        return representativePc < 0
                ? PruningEvaluation.unknown(
                -1, "no-invoke-evidence", "unknown")
                : PruningEvaluation.pruned(representativePc,
                "all-call-sites-exclude-callee", representativeReceiver);
    }

    @Override
    public ExtensionCounters counters() {
        return counters;
    }

    private record EdgeKey(CGNode caller, CGNode callee) {
    }
}
