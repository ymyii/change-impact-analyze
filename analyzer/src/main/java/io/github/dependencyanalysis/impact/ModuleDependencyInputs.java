package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.List;
import java.util.Objects;

/**
 * External dependency inputs and the planned target method-body scope.
 *
 * @param targetArtifacts target external artifacts
 * @param baselineArtifacts baseline external artifacts
 * @param changedPathSelection target dependency path selection
 */
public record ModuleDependencyInputs(
        List<ArtifactCoord> targetArtifacts,
        List<ArtifactCoord> baselineArtifacts,
        ModuleChangedPathSelection changedPathSelection) {

    /** Snapshots dependency inputs. */
    public ModuleDependencyInputs {
        targetArtifacts = List.copyOf(Objects.requireNonNull(
                targetArtifacts, "targetArtifacts"));
        baselineArtifacts = List.copyOf(Objects.requireNonNull(
                baselineArtifacts, "baselineArtifacts"));
        Objects.requireNonNull(changedPathSelection,
                "changedPathSelection");
    }
}
