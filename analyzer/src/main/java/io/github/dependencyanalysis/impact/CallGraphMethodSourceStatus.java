package io.github.dependencyanalysis.impact;

/** Availability of benchmark CGNode source evidence. */
public enum CallGraphMethodSourceStatus {

    /** Vineflower produced Java-like source. */
    DECOMPILED,

    /** Vineflower failed and ASM instructions were produced. */
    ASM_FALLBACK,

    /** No exact bytecode source was available. */
    UNAVAILABLE
}
