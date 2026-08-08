package io.github.dependencyanalysis.callgraph;

/**
 * Kind of call graph edge representing
 * the type of method invocation or
 * override relationship.
 */
public enum EdgeKind {

    /** Virtual method invocation. */
    INVOKE_VIRTUAL,

    /** Static method invocation. */
    INVOKE_STATIC,

    /** Special invocation including
     * constructors, private methods,
     * and super calls. */
    INVOKE_SPECIAL,

    /** Interface method invocation. */
    INVOKE_INTERFACE,

    /** Method override relationship. */
    OVERRIDE,

    /** ServiceLoader provider edge. */
    SERVICE,

    /** ServiceLoader fixed-point synthetic summary edge. */
    SERVICE_LOADER,

    /** Literal Class.forName edge. */
    REFLECTION_LITERAL,

    /** Terminal edge to an existing changed method. */
    METHOD_CHANGE,

    /** Terminal edge for a removed or changed declared invoke target. */
    DECLARED_INVOKE_REFERENCE,

    /** Terminal edge for a changed field reference. */
    FIELD_REFERENCE,

    /** Terminal edge for a changed type reference. */
    TYPE_REFERENCE,

    /** Terminal reference to an invokedynamic bootstrap method. */
    INVOKEDYNAMIC_BOOTSTRAP,

    /** Terminal reference to a direct bootstrap method-handle argument. */
    INVOKEDYNAMIC_HANDLE_REFERENCE,

    /** Direct target resolved by the RTA local MethodHandle model. */
    METHOD_HANDLE_TARGET,
}
