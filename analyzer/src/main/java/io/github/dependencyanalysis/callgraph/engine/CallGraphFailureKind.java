package io.github.dependencyanalysis.callgraph.engine;

/** Machine-readable Call Graph failure category. */
public enum CallGraphFailureKind {
    /** General construction or input failure. */
    GENERAL,
    /** Cooperative fixed-point timeout. */
    TIMEOUT
}
