package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.AccessTransition;

import java.util.Objects;

/**
 * Typed evidence for one JVM access observation.
 *
 * @param transition old/new normalized access
 * @param decision access decision
 * @param reason typed decision reason
 * @param callerIdentity caller method or referring class
 * @param declaringClass resolved declaration class
 * @param symbolicOwner constant-pool symbolic owner
 * @param receiverType local verifier receiver identity
 * @param referenceIdentity exact referenced member/type identity
 */
public record AccessReferenceEvidence(
        AccessTransition transition,
        AccessDecision decision,
        AccessDecisionReason reason,
        String callerIdentity,
        String declaringClass,
        String symbolicOwner,
        String receiverType,
        String referenceIdentity) implements ImpactEvidence {

    /** Validates typed access evidence. */
    public AccessReferenceEvidence {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(callerIdentity, "callerIdentity");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(symbolicOwner, "symbolicOwner");
        Objects.requireNonNull(receiverType, "receiverType");
        Objects.requireNonNull(referenceIdentity, "referenceIdentity");
    }

    @Override
    public String stableKey() {
        return transition.stableKey() + "|" + decision + "|" + reason
                + "|" + callerIdentity + "|" + declaringClass + "|"
                + symbolicOwner + "|" + receiverType + "|"
                + referenceIdentity;
    }

    @Override
    public String render() {
        final String prefix = decision
                == AccessDecision.POTENTIALLY_INACCESSIBLE
                ? "Potential access incompatibility" : "Access decision";
        return prefix + ": access=" + transition.stableKey()
                + "; decision=" + decision + "; reason=" + reason
                + "; caller=" + callerIdentity + "; declaration="
                + declaringClass + "; symbolicOwner=" + symbolicOwner
                + "; receiver=" + receiverType + "; reference="
                + referenceIdentity;
    }
}
