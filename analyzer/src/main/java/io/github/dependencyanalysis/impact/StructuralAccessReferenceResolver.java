package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.List;
import java.util.Optional;

/** Resolves raw Structural Reference access using target CHA only. */
final class StructuralAccessReferenceResolver {

    Resolution resolve(
            final StructuralReferenceMatch match,
            final ModuleCallGraphSession session) {
        final ChangePoint change = match.changePoint().getChangePoint();
        if (change.getKind() != ChangePointKind.CLASS_ACCESS_NARROWED) {
            return new Resolution(Optional.empty(), List.of());
        }
        final StructuralReference reference = match.reference();
        final IClass caller = lookupClass(
                session, reference.getReferencingClass());
        final IClass target = lookupClass(session, change.getOwner());
        if (caller == null) {
            return unresolved(match,
                    "Referring class is absent: "
                            + reference.getReferencingClass());
        }
        if (target == null) {
            return unresolved(match,
                    "Changed class is absent: " + change.getOwner());
        }
        final AccessTransition transition = change.getAccessTransition()
                .orElseThrow();
        final AccessCheckResult result = new JvmAccessChecker(
                session.getHierarchy()).check(new AccessCheckRequest(
                        caller, target, target, transition.newAccess(),
                        JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable()));
        return new Resolution(Optional.of(new AccessReferenceEvidence(
                transition, result.decision(), result.reason(),
                reference.getReferencingClass(), change.getOwner(),
                change.getOwner(), ReceiverType.notApplicable().stableKey(),
                reference.stableKey())), List.of());
    }

    private Resolution unresolved(
            final StructuralReferenceMatch match,
            final String detail) {
        return new Resolution(Optional.empty(), List.of(
                new QueryLimitation("ACCESS_TARGET_TYPE_UNRESOLVED",
                        ModuleAnalysisReason
                                .INCONCLUSIVE_SCOPE_VALIDATION,
                        match.changePoint().stableKey(), detail)));
    }

    private IClass lookupClass(
            final ModuleCallGraphSession session,
            final String expectedOwner) {
        for (IClass type : session.getHierarchy()) {
            final String name = type.getReference().getName().toString();
            final String owner = name.startsWith("L")
                    ? name.substring(1) : name;
            if (expectedOwner.equals(owner)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Typed structural access resolution.
     *
     * @param evidence access evidence when applicable and resolved
     * @param limitations target CHA resolution failures
     */
    record Resolution(
            Optional<AccessReferenceEvidence> evidence,
            List<QueryLimitation> limitations) {

        Resolution {
            evidence = java.util.Objects.requireNonNull(
                    evidence, "evidence");
            limitations = List.copyOf(limitations);
        }
    }
}
