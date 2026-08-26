package io.github.dependencyanalysis.tree;

/** Availability of one side of a tree comparison. */
enum TreeDiffSideState {
    /** Side exists and dependency evidence is available. */
    PRESENT,
    /** Inventory succeeded and the object does not exist. */
    ABSENT,
    /** Availability or dependency evidence cannot be determined. */
    UNAVAILABLE
}
