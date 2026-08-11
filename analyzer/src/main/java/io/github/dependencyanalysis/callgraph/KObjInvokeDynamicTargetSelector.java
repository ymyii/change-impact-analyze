package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;

import java.util.Objects;

/** k-object-sensitive invokedynamic execution Decorator. */
final class KObjInvokeDynamicTargetSelector
        implements MethodTargetSelector {

    /** Previously installed WALA selector. */
    private final MethodTargetSelector next;

    /** Immutable exact bootstrap registry. */
    private final InvokeDynamicBootstrapModelRegistry registry;

    /** Strategy-local build metadata. */
    private final InvokeDynamicModelState state;

    KObjInvokeDynamicTargetSelector(
            final MethodTargetSelector delegate,
            final InvokeDynamicBootstrapModelRegistry models,
            final InvokeDynamicModelState modelState) {
        next = Objects.requireNonNull(delegate, "delegate");
        registry = Objects.requireNonNull(models, "models");
        state = Objects.requireNonNull(modelState, "modelState");
    }

    @Override
    public IMethod getCalleeTarget(
            final CGNode caller,
            final CallSiteReference site,
            final IClass receiver) {
        final InvokeDynamicResolution resolution =
                InvokeDynamicModelResolver.resolve(caller, site, registry);
        resolution.evidence().forEach(state::addEvidence);
        resolution.limitations().forEach(state::addLimitation);
        return switch (resolution.disposition()) {
            case DELEGATE -> next.getCalleeTarget(caller, site, receiver);
            case TARGET -> resolution.target().orElseThrow();
            case NO_TARGET -> null;
        };
    }
}
