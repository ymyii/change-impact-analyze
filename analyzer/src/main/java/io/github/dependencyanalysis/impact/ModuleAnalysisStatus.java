package io.github.dependencyanalysis.impact;

/** Per-module analysis outcome. */
public enum ModuleAnalysisStatus {

    /** Module completed within the declared model. */
    SUCCESS,

    /** Module completed with conservative uncertainty. */
    INCONCLUSIVE,

    /** Module was intentionally not analyzed. */
    SKIPPED,

    /** Module analysis failed. */
    FAILED
}
