package io.github.dependencyanalysis.callgraph.scope;

/** Effective external dependency method-body scope. */
public enum DependencyScopeMode {
    /** Every selected external artifact retains real method bodies. */
    FULL,
    /** Only artifacts on changed dependency paths retain real bodies. */
    CHANGED_PATHS
}
