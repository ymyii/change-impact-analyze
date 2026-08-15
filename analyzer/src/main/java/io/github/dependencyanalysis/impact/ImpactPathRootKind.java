package io.github.dependencyanalysis.impact;

/** Root semantics for one retained Impact Path. */
public enum ImpactPathRootKind {

    /** A single PROJECT method without a real caller. */
    METHOD,

    /** A deterministic PROJECT representative of a root call cycle. */
    STRONGLY_CONNECTED_COMPONENT
}
