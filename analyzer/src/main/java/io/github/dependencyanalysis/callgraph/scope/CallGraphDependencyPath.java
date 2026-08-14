package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Minimal immutable dependency-path evidence required by the boundary.
 *
 * @param seed changed artifact at the path end
 * @param stablePath stable path representation
 */
public record CallGraphDependencyPath(
        ArtifactCoord seed,
        String stablePath) {

    /** Validates the path projection. */
    public CallGraphDependencyPath {
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(stablePath, "stablePath");
    }
}
