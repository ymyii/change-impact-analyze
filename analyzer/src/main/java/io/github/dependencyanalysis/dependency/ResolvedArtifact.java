package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.Objects;

/** Canonical physical dependency artifact binding. */
public final class ResolvedArtifact {

    /** Resolved artifact coordinate. */
    private final ArtifactCoord artifact;

    /** Canonical physical path. */
    private final Path path;

    /**
     * Creates a resolved artifact.
     *
     * @param value artifact coordinate
     * @param physicalPath canonical physical path
     */
    public ResolvedArtifact(
            final ArtifactCoord value,
            final Path physicalPath) {
        artifact = Objects.requireNonNull(value, "artifact");
        path = Objects.requireNonNull(physicalPath, "path")
                .toAbsolutePath().normalize();
    }

    /** @return artifact coordinate */
    public ArtifactCoord getArtifact() {
        return artifact;
    }

    /** @return canonical physical path */
    public Path getPath() {
        return path;
    }

    /** @return logical coordinate identity */
    public String bindingKey() {
        return artifact.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedArtifact)) {
            return false;
        }
        final ResolvedArtifact that = (ResolvedArtifact) other;
        return artifact.equals(that.artifact)
                && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact, path);
    }
}
