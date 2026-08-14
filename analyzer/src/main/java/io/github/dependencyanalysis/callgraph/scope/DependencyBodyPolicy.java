package io.github.dependencyanalysis.callgraph.scope;

/** Call Graph treatment of one external artifact's method bodies. */
public enum DependencyBodyPolicy {
    /** Traverse original bytecode bodies. */
    REAL_IR,
    /** Replace bodies with conservative no-op summaries. */
    NO_OP
}
