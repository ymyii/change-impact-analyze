package io.github.dependencyanalysis.preflight;

/** Observable check status. */
public enum PreflightStatus {
    /** Check succeeded. */
    PASS,

    /** Check continued with warning or fallback. */
    WARN,

    /** Required check failed. */
    FAIL,

    /** Dependency prevented execution. */
    SKIPPED
}
