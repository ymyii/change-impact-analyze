package io.github.dependencyanalysis.tree;

/** Lifecycle state exposed by a dependency tree diff report. */
enum TreeDiffReportState {
    /** Report publication is in progress. */
    RUNNING,
    /** Every Module was comparable. */
    SUCCESS,
    /** Comparable Modules and local issues both exist. */
    COMPLETED_WITH_ISSUES,
    /** No comparable Module or the publication pipeline failed. */
    FAILED
}
