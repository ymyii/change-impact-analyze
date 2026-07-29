package io.github.dependencyanalysis.preflight;

/** Failure handling requirement. */
public enum PreflightRequirement {
    /** Failure blocks the relevant scope. */
    REQUIRED,

    /** Failure enables an explicit fallback. */
    DEGRADABLE
}
