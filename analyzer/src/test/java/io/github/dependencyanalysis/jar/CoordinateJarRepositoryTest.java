package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests deterministic command-scoped coordinate bindings. */
class CoordinateJarRepositoryTest {

    /** Test artifact. */
    private static final ArtifactCoord ARTIFACT = new ArtifactCoord(
            "example", "library", "jar", "1");

    /** Temporary JAR fixtures. */
    @TempDir
    private Path temporary;

    @Test
    void deduplicatesSameCoordinateAndCanonicalPath() throws Exception {
        final Path jar = jar("same.jar", "same");
        final List<String> warnings = new ArrayList<>();

        try (IJarRepository repository = CoordinateJarRepository.create(
                List.of(new ResolvedArtifact(ARTIFACT, jar),
                        new ResolvedArtifact(ARTIFACT, jar)),
                warnings::add)) {
            assertThat(repository.coordinates()).containsExactly(ARTIFACT);
            assertThat(content(repository)).isEqualTo("same");
        }
        assertThat(warnings).isEmpty();
    }

    @Test
    void selectsLexicographicallyFirstCanonicalPath() throws Exception {
        final Path second = jar("z.jar", "second");
        final Path first = jar("a.jar", "first");
        final List<String> warnings = new ArrayList<>();

        try (IJarRepository repository = CoordinateJarRepository.create(
                List.of(new ResolvedArtifact(ARTIFACT, second),
                        new ResolvedArtifact(ARTIFACT, first)),
                warnings::add)) {
            assertThat(content(repository)).isEqualTo("first");
        }
        assertThat(warnings).singleElement().asString()
                .contains("artifact=" + ARTIFACT)
                .contains("selected=" + first.toRealPath());
    }

    @Test
    void orderDoesNotAffectConflictWinner() throws Exception {
        final Path first = jar("a-order.jar", "first");
        final Path second = jar("z-order.jar", "second");

        try (IJarRepository forward = CoordinateJarRepository.create(
                     List.of(new ResolvedArtifact(ARTIFACT, first),
                             new ResolvedArtifact(ARTIFACT, second)),
                     java.util.Objects::requireNonNull);
             IJarRepository reverse = CoordinateJarRepository.create(
                     List.of(new ResolvedArtifact(ARTIFACT, second),
                             new ResolvedArtifact(ARTIFACT, first)),
                     java.util.Objects::requireNonNull)) {
            assertThat(content(forward)).isEqualTo("first");
            assertThat(content(reverse)).isEqualTo("first");
        }
    }

    @Test
    void rejectsInvalidJarDuringConstruction() throws Exception {
        final Path invalid = temporary.resolve("invalid.jar");
        Files.writeString(invalid, "not a jar");

        assertThatThrownBy(() -> CoordinateJarRepository.create(
                List.of(new ResolvedArtifact(ARTIFACT, invalid)),
                java.util.Objects::requireNonNull))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void closeReleasesLeaseAndRejectsFurtherOpen() throws Exception {
        final Path jar = jar("close.jar", "content");
        final IJarRepository repository = CoordinateJarRepository.create(
                List.of(new ResolvedArtifact(ARTIFACT, jar)),
                java.util.Objects::requireNonNull);
        final JarLease lease = repository.open(ARTIFACT);

        repository.close();

        assertThatThrownBy(lease::jarFile)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.open(ARTIFACT))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void missingCoordinateFailsFastOnOpen() throws Exception {
        try (IJarRepository repository = CoordinateJarRepository.create(
                List.of(), java.util.Objects::requireNonNull)) {
            assertThatThrownBy(() -> repository.open(ARTIFACT))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("unavailable");
        }
    }

    private String content(final IJarRepository repository)
            throws Exception {
        try (JarLease lease = repository.open(ARTIFACT);
             InputStream input = lease.jarFile().getInputStream(
                     lease.jarFile().getJarEntry("value.txt"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path jar(final String name, final String content)
            throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("value.txt"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}
