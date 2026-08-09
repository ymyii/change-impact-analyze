package io.github.dependencyanalysis.callgraph;

/** Root kind for one deterministic shortest CGNode chain. */
public enum CallGraphPathRootKind {

    /** Declared application entrypoint. */
    DECLARED_ENTRYPOINT,

    /** WALA fake root node. */
    FAKE_ROOT,

    /** WALA fake world-clinit node. */
    FAKE_WORLD_CLINIT
}
