package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.Objects;

/** Stable module identity combining Maven coordinate and reactor path. */
public final class ModuleId implements Comparable<ModuleId> {

    /** Module coordinate. */
    private final ArtifactCoord coordinate;

    /** Reactor-relative module directory. */
    private final Path relativePath;

    /**
     * Creates a module identity.
     *
     * @param value module coordinate
     * @param path reactor-relative path
     */
    public ModuleId(final ArtifactCoord value, final Path path) {
        coordinate = Objects.requireNonNull(value, "coordinate");
        relativePath = Objects.requireNonNull(path, "relativePath")
                .normalize();
    }

    /** @return module coordinate */
    public ArtifactCoord getCoordinate() {
        return coordinate;
    }

    /** @return reactor-relative path */
    public Path getRelativePath() {
        return relativePath;
    }

    /** @return version-independent coordinate key */
    public String coordinateKey() {
        return coordinate.diffKey();
    }

    /** @return deterministic combined key */
    public String stableKey() {
        return coordinateKey() + "@"
                + relativePath.toString().replace('\\', '/');
    }

    @Override
    public int compareTo(final ModuleId other) {
        return stableKey().compareTo(other.stableKey());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModuleId)) {
            return false;
        }
        final ModuleId that = (ModuleId) other;
        return coordinateKey().equals(that.coordinateKey())
                && relativePath.equals(that.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinateKey(), relativePath);
    }

    @Override
    public String toString() {
        return stableKey();
    }
}
