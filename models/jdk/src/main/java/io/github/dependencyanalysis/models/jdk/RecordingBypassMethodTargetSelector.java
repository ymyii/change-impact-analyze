package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassMethodTargetSelector;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exact bypass selector that records modeled targets selected by WALA. */
final class RecordingBypassMethodTargetSelector
        extends BypassMethodTargetSelector {

    /** Exact modeled targets. */
    private final Set<MethodReference> targets;

    /** Per-graph recorder. */
    private final JdkModelSession session;

    RecordingBypassMethodTargetSelector(
            final MethodTargetSelector parent,
            final Map<MethodReference, MethodSummary> summaries,
            final IClassHierarchy hierarchy,
            final JdkModelSession modelSession) {
        super(parent, summaries, new LinkedHashSet<Atom>(), hierarchy);
        targets = Set.copyOf(summaries.keySet());
        session = modelSession;
    }

    @Override
    public IMethod getCalleeTarget(
            final CGNode caller,
            final CallSiteReference site,
            final IClass dispatchType) {
        final MethodReference declaredTarget = site.getDeclaredTarget();
        if (targets.contains(declaredTarget)) {
            final IMethod exact = findOrCreateSyntheticMethod(
                    declaredTarget, site.isStatic());
            session.recordHit(declaredTarget);
            return exact;
        }
        if (dispatchType != null) {
            final IMethod resolved = cha.resolveMethod(
                    dispatchType, declaredTarget.getSelector());
            if (resolved != null
                    && targets.contains(resolved.getReference())) {
                final IMethod inherited = findOrCreateSyntheticMethod(
                        resolved, site.isStatic());
                session.recordHit(resolved.getReference());
                return inherited;
            }
        }
        final IMethod result = super.getCalleeTarget(
                caller, site, dispatchType);
        if (result != null && targets.contains(result.getReference())) {
            session.recordHit(result.getReference());
        }
        return result;
    }
}
