package io.github.dependencyanalysis.impact;

/** Result of normalized WALA SSA comparison. */
public enum MethodEquivalenceStatus {

    /** The complete supported normalized model is isomorphic. */
    PROVEN_EQUIVALENT,

    /** The normalized models differ. */
    DIFFERENT,

    /** Equivalence could not be decided safely. */
    UNKNOWN
}
