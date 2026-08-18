package io.github.dependencyanalysis.classpath;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Logical classpath source without dependency JAR physical paths. */
public final class ClassSource {

    /** Dependency artifact, or null for a filesystem source. */
    private final ArtifactCoord artifact;

    /** Non-dependency filesystem source, or null for an artifact. */
    private final Path path;

    private ClassSource(
            final ArtifactCoord coordinate,
            final Path filesystemPath) {
        artifact = coordinate;
        path = filesystemPath;
    }

    /**
     * @param coordinate dependency coordinate
     * @return dependency artifact source
     */
    public static ClassSource artifact(final ArtifactCoord coordinate) {
        return new ClassSource(Objects.requireNonNull(
                coordinate, "coordinate"), null);
    }

    /**
     * @param value non-dependency filesystem path
     * @return project, reactor, or JDK filesystem source
     */
    public static ClassSource path(final Path value) {
        return new ClassSource(null, Objects.requireNonNull(value, "path")
                .toAbsolutePath().normalize());
    }

    /** @return artifact when this is a dependency JAR source */
    public Optional<ArtifactCoord> artifact() {
        return Optional.ofNullable(artifact);
    }

    /** @return path when this is a non-dependency source */
    public Optional<Path> path() {
        return Optional.ofNullable(path);
    }

    /** @return stable report and identity value */
    public String stableKey() {
        return artifact == null ? path.toString() : artifact.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClassSource)) {
            return false;
        }
        final ClassSource that = (ClassSource) other;
        return Objects.equals(artifact, that.artifact)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact, path);
    }

    @Override
    public String toString() {
        return stableKey();
    }
}
