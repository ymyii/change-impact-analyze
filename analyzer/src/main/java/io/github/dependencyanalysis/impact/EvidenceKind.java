package io.github.dependencyanalysis.impact;

/** Semantic target category of one ChangePoint reference. */
public enum EvidenceKind {
    /** Method declaration or invocation reference. */
    METHOD_REFERENCE,
    /** Field bytecode reference. */
    FIELD_REFERENCE,
    /** Type or class reference. */
    TYPE_REFERENCE,
    /** Class-file structural metadata reference. */
    STRUCTURAL_REFERENCE,
    /** Resource registration reference. */
    RESOURCE_REFERENCE
}
