package io.github.dependencyanalysis.impact;

/** Availability of user-reviewable old/new code evidence. */
public enum CodeComparisonStatus {

    /** Decompiled Java representations produced a Unified diff. */
    AVAILABLE,

    /** Decompiled text was identical; ASM evidence produced the diff. */
    ASM_FALLBACK,

    /** Decompiled Java comparison could not be produced. */
    UNAVAILABLE
}
