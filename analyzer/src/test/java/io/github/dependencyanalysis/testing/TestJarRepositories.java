package io.github.dependencyanalysis.testing;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.jar.CoordinateJarRepository;
import io.github.dependencyanalysis.jar.IJarRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Test-only repository construction helpers. */
public final class TestJarRepositories {

    private TestJarRepositories() {
    }

    /**
     * Creates a repository without diagnostics.
     *
     * @param artifacts physical test fixtures
     * @return repository
     * @throws IOException invalid fixture JAR
     */
    public static IJarRepository of(
            final List<ResolvedArtifact> artifacts) throws IOException {
        return CoordinateJarRepository.create(artifacts,
                java.util.Objects::requireNonNull);
    }

    /**
     * Creates an empty repository.
     *
     * @return empty repository
     * @throws IOException unexpected construction failure
     */
    public static IJarRepository empty() throws IOException {
        return of(List.of());
    }

    /**
     * Creates a repository containing one old/new test pair.
     *
     * @param oldArtifact old coordinate
     * @param oldJar old fixture
     * @param newArtifact new coordinate
     * @param newJar new fixture
     * @return pair repository
     * @throws IOException invalid fixture JAR
     */
    public static IJarRepository pair(
            final ArtifactCoord oldArtifact,
            final Path oldJar,
            final ArtifactCoord newArtifact,
            final Path newJar) throws IOException {
        return of(List.of(
                new ResolvedArtifact(oldArtifact, oldJar),
                new ResolvedArtifact(newArtifact, newJar)));
    }
}
