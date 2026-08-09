package io.github.dependencyanalysis.callgraph;

/** WALA sentinel role carried by one exact CGNode. */
public enum CallGraphNodeSentinelRole {

    /** Ordinary Call Graph node. */
    NONE,

    /** WALA fake root node. */
    FAKE_ROOT,

    /** WALA fake world-clinit node. */
    FAKE_WORLD_CLINIT
}
