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

    /** Conservative ServiceLoader overlay edge. */
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
}
