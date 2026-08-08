package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;

import java.util.Objects;
import java.util.Optional;

/** RTA MethodHandle selector using caller-local IR facts only. */
final class RtaMethodHandleTargetSelector implements MethodTargetSelector {

    /** Previously installed selector. */
    private final MethodTargetSelector base;

    /** Local fact resolver. */
    private final RtaMethodHandleFactResolver resolver;

    /** Build-time metadata. */
    private final RtaMethodHandleModelState state;

    /** Stable bridge summary factory. */
    private final RtaMethodHandleSummaryFactory summaries;

    RtaMethodHandleTargetSelector(
            final MethodTargetSelector delegate,
            final RtaMethodHandleFactResolver factResolver,
            final RtaMethodHandleModelState modelState,
            final RtaMethodHandleSummaryFactory summaryFactory) {
        base = Objects.requireNonNull(delegate, "delegate");
        resolver = Objects.requireNonNull(factResolver, "factResolver");
        state = Objects.requireNonNull(modelState, "modelState");
        summaries = Objects.requireNonNull(summaryFactory, "summaryFactory");
    }

    @Override
    public IMethod getCalleeTarget(
            final CGNode caller,
            final CallSiteReference site,
            final IClass receiver) {
        final Optional<IMethod> bridge = summaries.find(
                site.getDeclaredTarget());
        if (bridge.isPresent()) {
            return bridge.orElseThrow();
        }
        final LocalMethodHandleResolution resolution = resolver.resolve(
                caller, site);
        if (resolution.status()
                == LocalMethodHandleResolution.Status.NOT_APPLICABLE) {
            return base.getCalleeTarget(caller, site, receiver);
        }
        if (resolution.status()
                == LocalMethodHandleResolution.Status.UNSUPPORTED) {
            state.addLimitation(
                    resolution.location(),
                    resolution.detail());
            return base.getCalleeTarget(caller, site, receiver);
        }
        final IMethod target = resolution.targetMethod().orElseThrow();
        state.addEvidence(new DynamicCallEvidence(
                resolution.targetOwner(), resolution.targetName(),
                resolution.targetDescriptor(), caller,
                resolution.bytecodePc(), EdgeKind.METHOD_HANDLE_TARGET,
                DynamicReferenceKind.DIRECT_MODELED_HANDLE_TARGET,
                Optional.of(MethodHandleReferenceKind.REF_INVOKE_STATIC),
                "RTA_METHOD_HANDLE_LOCAL_TARGET|operation="
                        + resolution.operation() + "; target="
                        + target.getReference()));
        return summaries.bridge(resolution, site);
    }
}
