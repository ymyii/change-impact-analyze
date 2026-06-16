package io.github.changeimpact.analyze.dependency;

/**
 * Type of dependency change between
 * baseline and target resolved trees.
 * Ordinal order defines sort priority.
 */
public enum ChangeType {

    /** Dependency was added. */
    ADDED,

    /** Dependency was removed. */
    REMOVED,

    /** Dependency version changed. */
    VERSION_CHANGED
}
