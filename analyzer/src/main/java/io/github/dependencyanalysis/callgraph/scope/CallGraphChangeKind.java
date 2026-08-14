package io.github.dependencyanalysis.callgraph.scope;

/** Change categories consumed by Call Graph dispatch policy. */
public enum CallGraphChangeKind {
    /** A target method was added. */
    METHOD_ADDED,
    /** A target method body changed. */
    METHOD_BODY_CHANGED,
    /** A target method became less accessible. */
    METHOD_ACCESS_NARROWED,
    /** Change is irrelevant to dispatch retention. */
    OTHER
}
