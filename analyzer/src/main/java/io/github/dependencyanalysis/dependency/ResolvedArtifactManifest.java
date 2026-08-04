package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Validated module-local Artifact Path Schema v2 document. */
public final class ResolvedArtifactManifest {

    /** Canonical directory containing the manifest. */
    private final Path directory;

    /** External artifact bindings. */
    private final List<ResolvedArtifact> artifacts;

    /**
     * Creates a validated manifest.
     *
     * @param manifestDirectory directory containing the manifest
     * @param resolvedArtifacts artifact bindings
     */
    public ResolvedArtifactManifest(
            final Path manifestDirectory,
            final List<ResolvedArtifact> resolvedArtifacts) {
        directory = Objects.requireNonNull(
                manifestDirectory, "manifestDirectory")
                .toAbsolutePath().normalize();
        artifacts = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(
                        resolvedArtifacts, "resolvedArtifacts")));
    }

    /** @return canonical directory containing the manifest */
    public Path getDirectory() {
        return directory;
    }

    /** @return immutable external artifact bindings */
    public List<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }
}
