package io.github.dependencyanalysis.tree;

/** Dependency-level resolved version classification. */
enum TreeDependencyBaseChangeType {
    /** Resolved version differs. */
    VERSION_CHANGED,
    /** Dependency exists only in target. */
    ADDED,
    /** Dependency exists only in baseline. */
    REMOVED,
    /** Resolved version is unchanged. */
    RESOLVED_UNCHANGED
}
