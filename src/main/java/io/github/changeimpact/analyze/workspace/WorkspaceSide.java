package io.github.changeimpact.analyze.workspace;

/**
 * Identifies which side of a comparison
 * a workspace belongs to.
 */
public enum WorkspaceSide {

    /** The baseline (older) side. */
    BASELINE,

    /** The target (newer) side. */
    TARGET,

    /** The current working tree. */
    CURRENT
}
