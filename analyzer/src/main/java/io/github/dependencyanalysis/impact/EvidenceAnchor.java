package io.github.dependencyanalysis.impact;

/** Stable location anchor for algorithm-independent terminal evidence. */
public sealed interface EvidenceAnchor permits MethodEvidenceAnchor,
        StructuralEvidenceAnchor, ResourceEvidenceAnchor {

    /** @return stable identity independent of graph node numbering */
    String stableKey();
}
