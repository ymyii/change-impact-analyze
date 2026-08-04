package io.github.dependencyanalysis.preflight;

/** Execution decision produced by preflight. */
public enum PreflightDecision {
    /** Continue normally. */
    CONTINUE,

    /** Continue with documented fallback. */
    DEGRADE,

    /** Skip one reactor. */
    BLOCK_REACTOR,

    /** Do not start the command pipeline. */
    BLOCK_COMMAND
}
