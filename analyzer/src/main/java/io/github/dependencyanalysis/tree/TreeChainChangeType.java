package io.github.dependencyanalysis.tree;

/** PathKey existence classification for one dependency chain. */
enum TreeChainChangeType {
    /** Path exists only in target. */
    ADDED,
    /** Path exists only in baseline. */
    REMOVED,
    /** The same PathKey exists on both sides. */
    UNCHANGED
}
