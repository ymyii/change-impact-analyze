package io.github.dependencyanalysis.callgraph.strategy.kobj;

import com.ibm.wala.analysis.reflection.ClassFactoryContextSelector;
import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.DelegatingContext;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSite;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.AllocationString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.AllocationStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallerSiteContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ContainerContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjContextSelector;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;

import java.util.Objects;

/** WALA 1.8.0 n-object semantics with ClassFactory base precedence. */
public final class KObjContextSelector implements ContextSelector {

    /** Dispatch receiver parameter. */
    private static final IntSet RECEIVER_PARAMETER =
            IntSetUtil.make(new int[]{0});

    /** Maximum allocation-string depth. */
    private final int depth;

    /** Existing WALA default selector, including Reflection selectors. */
    private final ContextSelector base;

    /**
     * Creates a selector over an installed base selector.
     *
     * @param configuredDepth receiver allocation-string depth
     * @param baseSelector installed WALA selector
     */
    public KObjContextSelector(
            final int configuredDepth,
            final ContextSelector baseSelector) {
        if (configuredDepth <= 0) {
            throw new IllegalArgumentException("depth must be positive");
        }
        depth = configuredDepth;
        base = Objects.requireNonNull(baseSelector, "base");
    }

    @Override
    public Context getCalleeTarget(
            final CGNode caller,
            final CallSiteReference site,
            final IMethod callee,
            final InstanceKey[] actualParameters) {
        Context kObjContext = Everywhere.EVERYWHERE;
        final InstanceKey receiver = actualParameters != null
                && actualParameters.length > 0
                ? actualParameters[0] : null;
        if (site.isStatic()) {
            kObjContext = staticContext(caller, site, callee);
        } else if (receiver instanceof AllocationSiteInNode allocation) {
            kObjContext = new AllocationStringContext(
                    allocationString(allocation));
        }
        final Context baseContext = base.getCalleeTarget(
                caller, site, callee, actualParameters);
        return combine(callee.getReference(), kObjContext, baseContext);
    }

    private Context staticContext(
            final CGNode caller,
            final CallSiteReference site,
            final IMethod callee) {
        if (ContainerContextSelector.isWellKnownStaticFactory(
                callee.getReference())) {
            return new CallerSiteContext(caller, site);
        }
        return caller.getContext();
    }

    private AllocationString allocationString(
            final AllocationSiteInNode receiver) {
        final Context receiverContext = receiver.getNode().getContext();
        final AllocationSite current = new AllocationSite(
                receiver.getNode().getMethod(), receiver.getSite(),
                receiver.concreteType());
        final Object inherited = receiverContext.get(
                nObjContextSelector.ALLOCATION_STRING_KEY);
        if (inherited == null) {
            return new AllocationString(current);
        }
        final AllocationString previous = (AllocationString) inherited;
        final int length = Math.min(depth, previous.getLength() + 1);
        final AllocationSite[] sites = new AllocationSite[length];
        sites[0] = current;
        System.arraycopy(previous.allocationSites(), 0,
                sites, 1, length - 1);
        return new AllocationString(sites);
    }

    /**
     * Combines k-object and WALA base contexts.
     *
     * @param callee resolved callee
     * @param kObjContext allocation-sensitive context
     * @param baseContext WALA base context
     * @return combined context
     */
    public static Context combine(
            final MethodReference callee,
            final Context kObjContext,
            final Context baseContext) {
        if (kObjContext == Everywhere.EVERYWHERE) {
            return baseContext;
        }
        if (baseContext == Everywhere.EVERYWHERE) {
            return kObjContext;
        }
        if (ClassFactoryContextSelector.isClassFactory(callee)
                && baseContext != null
                && baseContext.isA(JavaTypeContext.class)
                && baseContext.get(ContextKey.RECEIVER)
                instanceof TypeAbstraction) {
            return new DelegatingContext(baseContext, kObjContext);
        }
        return new DelegatingContext(kObjContext, baseContext);
    }

    @Override
    public IntSet getRelevantParameters(
            final CGNode caller,
            final CallSiteReference site) {
        return site.isDispatch()
                ? RECEIVER_PARAMETER : EmptyIntSet.instance;
    }
}
