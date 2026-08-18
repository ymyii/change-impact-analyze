package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One ordered, canonical Maven classpath source.
 *
 * @param order zero-based classpath order
 * @param origin logical source kind
 * @param coordinates logical Maven coordinate
 * @param scope Maven scope, empty only for PROJECT
 * @param physicalPath canonical analysis-only path
 */
public record ClasspathEvidenceEntry(
        int order,
        CodeOrigin origin,
        ArtifactCoord coordinates,
        String scope,
        Path physicalPath) {

    /** Validates one evidence entry. */
    public ClasspathEvidenceEntry {
        if (order < 0) {
            throw new IllegalArgumentException("Negative classpath order");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(coordinates, "coordinates");
        Objects.requireNonNull(scope, "scope");
        physicalPath = Objects.requireNonNull(
                physicalPath, "physicalPath").toAbsolutePath().normalize();
    }

    /** @return source label safe for a user-facing report */
    public String logicalSource() {
        return origin + " — " + coordinates;
    }
}
