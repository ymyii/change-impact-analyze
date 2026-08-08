package io.github.dependencyanalysis.impact;

/** Typed reason for a JVM access decision. */
public enum AccessDecisionReason {

    /** Public access. */
    PUBLIC_ACCESS,

    /** Caller and declaration share one runtime package. */
    SAME_RUNTIME_PACKAGE,

    /** Package-private access crosses a runtime-package boundary. */
    DIFFERENT_RUNTIME_PACKAGE,

    /** Java 8 private access from the declaring class. */
    DECLARING_CLASS,

    /** Java 8 private access from another class. */
    PRIVATE_MEMBER,

    /** Protected caller is not a subclass. */
    PROTECTED_CALLER_NOT_SUBCLASS,

    /** Protected static access from a qualifying subclass. */
    PROTECTED_STATIC_SUBCLASS,

    /** Protected access through this. */
    PROTECTED_THIS_RECEIVER,

    /** Protected invokespecial super access. */
    PROTECTED_SUPER_RECEIVER,

    /** Protected receiver verifier type is legal. */
    PROTECTED_RECEIVER_ASSIGNABLE,

    /** Protected receiver verifier type is illegal. */
    PROTECTED_RECEIVER_NOT_ASSIGNABLE,

    /** Protected receiver verifier type is incomplete. */
    PROTECTED_RECEIVER_UNKNOWN,

    /** Symbolic owner does not satisfy protected constraints. */
    PROTECTED_SYMBOLIC_OWNER
}
