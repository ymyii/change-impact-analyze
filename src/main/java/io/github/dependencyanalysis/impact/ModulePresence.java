package io.github.dependencyanalysis.impact;

/** Presence of a version-independent module coordinate across Git sides. */
public enum ModulePresence {

    /** Module exists on both sides. */
    BOTH,

    /** Module exists only on the baseline side. */
    BASELINE_ONLY,

    /** Module exists only on the target side. */
    TARGET_ONLY
}
