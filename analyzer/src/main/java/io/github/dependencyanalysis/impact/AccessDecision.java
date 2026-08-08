package io.github.dependencyanalysis.impact;

/** JVM access result for one target-side bytecode reference. */
public enum AccessDecision {

    /** Reference remains legal. */
    ACCESSIBLE,

    /** Reference is provably illegal. */
    INACCESSIBLE,

    /** Protected receiver legality cannot be proven from local IR. */
    POTENTIALLY_INACCESSIBLE
}
