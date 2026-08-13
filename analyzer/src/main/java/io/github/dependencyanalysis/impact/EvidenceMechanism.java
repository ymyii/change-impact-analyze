package io.github.dependencyanalysis.impact;

/** Exact mechanism that produced terminal ChangePoint evidence. */
public enum EvidenceMechanism {
    /** Reachable changed method declaration. */
    METHOD_DECLARATION,
    /** Declared bytecode invocation. */
    DECLARED_INVOKE,
    /** Declared bytecode field access. */
    BYTECODE_FIELD_REFERENCE,
    /** Explicit bytecode type reference. */
    BYTECODE_TYPE_REFERENCE,
    /** Caller-local String constant passed to Class.forName. */
    CLASS_FOR_NAME_LOCAL_CONSTANT,
    /** ServiceLoader provider relationship. */
    SERVICE_LOADER_PROVIDER,
    /** invokedynamic bootstrap method handle. */
    INVOKEDYNAMIC_BOOTSTRAP,
    /** invokedynamic bootstrap argument handle. */
    INVOKEDYNAMIC_HANDLE,
    /** Locally modeled MethodHandle target. */
    METHOD_HANDLE_TARGET,
    /** Class-file structural metadata. */
    STRUCTURAL_METADATA
}
