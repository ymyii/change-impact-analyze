package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;

import io.github.dependencyanalysis.bytecode.JvmAccess;

import java.util.Objects;

/** Stateless Java 8 JVM access policy over the target hierarchy. */
public final class JvmAccessChecker {

    /** Target hierarchy used only for relationship queries. */
    private final IClassHierarchy hierarchy;

    /** @param cha target class hierarchy */
    public JvmAccessChecker(final IClassHierarchy cha) {
        hierarchy = Objects.requireNonNull(cha, "cha");
    }

    /**
     * Evaluates new access for one pre-existing bytecode reference.
     *
     * @param request typed access query
     * @return access decision
     */
    public AccessCheckResult check(final AccessCheckRequest request) {
        Objects.requireNonNull(request, "request");
        final JvmAccess access = request.newAccess();
        if (access == JvmAccess.PUBLIC) {
            return result(AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.PUBLIC_ACCESS);
        }
        if (access == JvmAccess.PRIVATE) {
            return request.caller().equals(request.declaringClass())
                    ? result(AccessDecision.ACCESSIBLE,
                            AccessDecisionReason.DECLARING_CLASS)
                    : result(AccessDecision.INACCESSIBLE,
                            AccessDecisionReason.PRIVATE_MEMBER);
        }
        if (sameRuntimePackage(request.caller(),
                request.declaringClass())) {
            return result(AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.SAME_RUNTIME_PACKAGE);
        }
        if (access == JvmAccess.PACKAGE_PRIVATE) {
            return result(AccessDecision.INACCESSIBLE,
                    AccessDecisionReason.DIFFERENT_RUNTIME_PACKAGE);
        }
        return protectedAccess(request);
    }

    private AccessCheckResult protectedAccess(
            final AccessCheckRequest request) {
        if (!hierarchy.isSubclassOf(
                request.caller(), request.declaringClass())) {
            return result(AccessDecision.INACCESSIBLE,
                    AccessDecisionReason.PROTECTED_CALLER_NOT_SUBCLASS);
        }
        if (!symbolicOwnerAllowed(request.caller(),
                request.symbolicOwner())) {
            return result(AccessDecision.INACCESSIBLE,
                    AccessDecisionReason.PROTECTED_SYMBOLIC_OWNER);
        }
        if (!request.referenceKind().isInstance()) {
            return result(AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.PROTECTED_STATIC_SUBCLASS);
        }
        return switch (request.receiverType().kind()) {
            case THIS -> result(AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.PROTECTED_THIS_RECEIVER);
            case SUPER -> result(AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.PROTECTED_SUPER_RECEIVER);
            case POINT -> assignableReceiver(request,
                    request.receiverType().type());
            case CONE -> coneReceiver(request,
                    request.receiverType().type());
            case UNKNOWN, NOT_APPLICABLE -> result(
                    AccessDecision.POTENTIALLY_INACCESSIBLE,
                    AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN);
        };
    }

    private AccessCheckResult assignableReceiver(
            final AccessCheckRequest request,
            final IClass receiver) {
        return hierarchy.isAssignableFrom(request.caller(), receiver)
                ? result(AccessDecision.ACCESSIBLE,
                        AccessDecisionReason.PROTECTED_RECEIVER_ASSIGNABLE)
                : result(AccessDecision.INACCESSIBLE,
                        AccessDecisionReason
                                .PROTECTED_RECEIVER_NOT_ASSIGNABLE);
    }

    private AccessCheckResult coneReceiver(
            final AccessCheckRequest request,
            final IClass root) {
        return hierarchy.isAssignableFrom(request.caller(), root)
                ? result(AccessDecision.ACCESSIBLE,
                        AccessDecisionReason.PROTECTED_RECEIVER_ASSIGNABLE)
                : result(AccessDecision.POTENTIALLY_INACCESSIBLE,
                        AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN);
    }

    private boolean symbolicOwnerAllowed(
            final IClass caller,
            final IClass symbolicOwner) {
        return caller.equals(symbolicOwner)
                || hierarchy.isSubclassOf(symbolicOwner, caller)
                || hierarchy.isSubclassOf(caller, symbolicOwner);
    }

    private boolean sameRuntimePackage(
            final IClass left,
            final IClass right) {
        return left.getClassLoader().getReference().equals(
                right.getClassLoader().getReference())
                && packageName(left).equals(packageName(right));
    }

    private String packageName(final IClass type) {
        final String name = type.getName().toString();
        final int separator = name.lastIndexOf('/');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private AccessCheckResult result(
            final AccessDecision decision,
            final AccessDecisionReason reason) {
        return new AccessCheckResult(decision, reason);
    }
}
