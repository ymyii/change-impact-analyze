package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * Read-only metadata for one model-generated WALA edge.
 *
 * @param kind edge kind
 * @param evidence stable model evidence
 */
public record SyntheticEdgeMetadata(EdgeKind kind, String evidence) {

    /** Validates edge metadata. */
    public SyntheticEdgeMetadata {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(evidence, "evidence");
    }
}
