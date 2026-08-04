package io.github.dependencyanalysis.impact;

/** Directness classification based on the seed node origin. */
public enum ImpactClassification {

    /** Seed is declared by the current PROJECT module. */
    DIRECT,

    /** Seed is declared by reactor, external dependency, or JDK code. */
    TRANSITIVE
}
