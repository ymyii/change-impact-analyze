package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Validated Artifact Path Schema v1 document for one Maven module. */
public final class ResolvedArtifactManifest {

    /** Module coordinates. */
    private final ArtifactCoord module;

    /** Canonical module base directory. */
    private final Path baseDirectory;

    /** External artifact bindings. */
    private final List<ResolvedArtifact> artifacts;

    /**
     * Creates a validated manifest.
     *
     * @param moduleCoordinates module coordinates
     * @param moduleDirectory module base directory
     * @param resolvedArtifacts artifact bindings
     */
    public ResolvedArtifactManifest(
            final ArtifactCoord moduleCoordinates,
            final Path moduleDirectory,
            final List<ResolvedArtifact> resolvedArtifacts) {
        module = Objects.requireNonNull(
                moduleCoordinates, "moduleCoordinates");
        baseDirectory = Objects.requireNonNull(
                moduleDirectory, "moduleDirectory")
                .toAbsolutePath().normalize();
        artifacts = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(
                        resolvedArtifacts, "resolvedArtifacts")));
    }

    /** @return module coordinates */
    public ArtifactCoord getModule() {
        return module;
    }

    /** @return canonical module base directory */
    public Path getBaseDirectory() {
        return baseDirectory;
    }

    /** @return immutable external artifact bindings */
    public List<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }
}
