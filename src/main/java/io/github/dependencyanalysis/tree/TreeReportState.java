package io.github.dependencyanalysis.tree;

/** Lifecycle state exposed by an incremental tree report index. */
public enum TreeReportState {

    /** Reactor analysis is still running. */
    RUNNING,

    /** Every reactor completed without operational issues. */
    SUCCESS,

    /** Every reactor completed, with degraded or failed analyses. */
    COMPLETED_WITH_ISSUES,

    /** The report pipeline stopped after a handled failure. */
    FAILED
}
