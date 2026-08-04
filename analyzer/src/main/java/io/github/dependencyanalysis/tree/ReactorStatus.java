package io.github.dependencyanalysis.tree;

/** Final reactor execution status. */
public enum ReactorStatus {
    /** Complete dependency and mediation data. */
    SUCCESS,

    /** Resolved tree available with reduced evidence. */
    DEGRADED,

    /** Required reactor analysis failed. */
    FAILED
}
