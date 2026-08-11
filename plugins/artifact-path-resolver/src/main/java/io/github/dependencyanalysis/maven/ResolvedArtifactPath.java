package io.github.dependencyanalysis.maven;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** One selected external dependency and its Resolver-provided path. */
final class ResolvedArtifactPath {

    /** Stable Schema v3 artifact order. */
    static final Comparator<ResolvedArtifactPath> ORDER = Comparator
            .comparing(ResolvedArtifactPath::getCoordinates,
                    ArtifactCoordinates.ORDER)
            .thenComparing(value -> value.getAbsolutePath().toString());

    /** Coordinates. */
    private final ArtifactCoordinates coordinates;

    /** Resolved absolute file path. */
    private final Path absolutePath;

    ResolvedArtifactPath(
            final ArtifactCoordinates artifactCoordinates,
            final Path path) {
        coordinates = Objects.requireNonNull(
                artifactCoordinates, "coordinates");
        absolutePath = Objects.requireNonNull(path, "absolutePath");
    }

    ArtifactCoordinates getCoordinates() {
        return coordinates;
    }

    Path getAbsolutePath() {
        return absolutePath;
    }
}
