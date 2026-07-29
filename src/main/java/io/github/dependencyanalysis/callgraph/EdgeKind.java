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

    /** Literal Class.forName edge. */
    REFLECTION_LITERAL,
}
