package io.github.dependencyanalysis.maven;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** One selected external dependency and its Resolver-provided path. */
final class ResolvedArtifactPath {

    /** Stable Schema v1 artifact order. */
    static final Comparator<ResolvedArtifactPath> ORDER = Comparator
            .comparing(ResolvedArtifactPath::getCoordinates,
                    ArtifactCoordinates.ORDER)
            .thenComparing(ResolvedArtifactPath::getScope)
            .thenComparing(value -> value.getAbsolutePath().toString());

    /** Coordinates. */
    private final ArtifactCoordinates coordinates;

    /** Effective selected scope. */
    private final String scope;

    /** Resolved absolute file path. */
    private final Path absolutePath;

    ResolvedArtifactPath(
            final ArtifactCoordinates artifactCoordinates,
            final String dependencyScope,
            final Path path) {
        coordinates = Objects.requireNonNull(
                artifactCoordinates, "coordinates");
        scope = Objects.requireNonNull(dependencyScope, "scope");
        absolutePath = Objects.requireNonNull(path, "absolutePath");
    }

    ArtifactCoordinates getCoordinates() {
        return coordinates;
    }

    String getScope() {
        return scope;
    }

    Path getAbsolutePath() {
        return absolutePath;
    }
}
