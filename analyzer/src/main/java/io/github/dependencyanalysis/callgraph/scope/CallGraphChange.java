package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Minimal immutable changed-member projection used during graph building.
 *
 * @param artifact changed artifact
 * @param kind graph-relevant change kind
 * @param owner changed owner
 * @param name changed member name
 * @param descriptor changed member descriptor
 */
public record CallGraphChange(
        ArtifactCoord artifact,
        CallGraphChangeKind kind,
        String owner,
        String name,
        String descriptor) {

    /** Validates the change projection. */
    public CallGraphChange {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
    }
}
