package io.github.dependencyanalysis.impact;

/** Availability of user-reviewable old/new code evidence. */
public enum CodeComparisonStatus {

    /** Decompiled Java representations produced a Unified diff. */
    AVAILABLE,

    /** Decompiled Java representations are identical. */
    JAVA_TEXT_IDENTICAL,

    /** Decompiled Java comparison could not be produced. */
    UNAVAILABLE
}
