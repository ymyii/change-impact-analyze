package io.github.dependencyanalysis.impact;

/** Aggregate evidence result for one BoundChangePoint. */
public enum EvidenceResolutionStatus {
    /** At least one reference evidence was bound. */
    MATCHED,
    /** No relevant reachable reference was found. */
    NONE,
    /** Change kind has no reverse-impact semantics. */
    UNSUPPORTED,
    /** A relevant construct could not be resolved completely. */
    INCONCLUSIVE
}
