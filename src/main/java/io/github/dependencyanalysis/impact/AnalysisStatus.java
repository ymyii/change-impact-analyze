package io.github.dependencyanalysis.impact;

/** Overall impact analysis outcome. */
public enum AnalysisStatus {

    /** Every analyzed module completed within the declared model. */
    SUCCESS,

    /** Analysis completed but retained explicit uncertainty. */
    INCONCLUSIVE,

    /** Some modules completed and some failed. */
    PARTIAL_SUCCESS,

    /** No relevant module completed. */
    FAILED
}
