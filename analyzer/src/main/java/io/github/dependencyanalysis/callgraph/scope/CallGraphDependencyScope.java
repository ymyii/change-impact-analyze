package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable external artifact body policy and changed-path projection.
 *
 * @param mode effective dependency scope
 * @param artifactPolicies body policy by selected artifact
 * @param paths stable changed dependency paths
 */
public record CallGraphDependencyScope(
        DependencyScopeMode mode,
        Map<ArtifactCoord, DependencyBodyPolicy> artifactPolicies,
        List<CallGraphDependencyPath> paths) {

    /** Validates and freezes the dependency scope. */
    public CallGraphDependencyScope {
        Objects.requireNonNull(mode, "mode");
        artifactPolicies = Map.copyOf(Objects.requireNonNull(
                artifactPolicies, "artifactPolicies"));
        paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
    }

    /** @return effective body policy for one selected artifact */
    public DependencyBodyPolicy policyFor(final ArtifactCoord artifact) {
        final DependencyBodyPolicy policy = artifactPolicies.get(
                Objects.requireNonNull(artifact, "artifact"));
        if (policy == null) {
            throw new IllegalArgumentException(
                    "Artifact is not a selected target binding: " + artifact);
        }
        return policy;
    }
}
