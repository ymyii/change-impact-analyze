package io.github.dependencyanalysis.impact;

/** Aggregate reference observation for one ChangePoint. */
public enum ReferenceObservation {

    /** No relevant target-side reference was found. */
    NONE,

    /** Relevant references exist and all remain legal. */
    ACCESSIBLE_ONLY,

    /** At least one reference is inaccessible or potentially inaccessible. */
    IMPACTING
}
