package io.github.dependencyanalysis.bytecode;

/** Outcome of one normalized SSA/CFG method comparison. */
public enum SsaComparisonStatus {

    /** The supported normalized model matched; suppress the ChangePoint. */
    MATCHED,

    /** The supported normalized model differed; retain the ChangePoint. */
    DIFFERENT,

    /** Comparison could not establish a result; retain the ChangePoint. */
    UNKNOWN
}
