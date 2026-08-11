package io.github.dependencyanalysis.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Command-owned Dependency Evidence directory boundary tests. */
class DependencyEvidenceOutputDirectoryTest {

    /** Temporary root. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void acceptsMatchingOwnerOutsideSource() throws Exception {
        final Path source = Files.createDirectory(
                temporaryDirectory.resolve("source"));
        final Path evidence = evidence("evidence", "owner");

        assertThat(DependencyEvidenceOutputDirectory.validate(
                evidence.toString(), "owner", source))
                .isEqualTo(evidence.toRealPath());
    }

    @Test
    void rejectsOwnerMismatchAndSourceWorkspaceOutput() throws Exception {
        final Path source = Files.createDirectory(
                temporaryDirectory.resolve("source"));
        final Path evidence = evidence("evidence", "owner");
        final Path inside = Files.createDirectory(source.resolve("cache"));
        Files.write(inside.resolve(
                        DependencyEvidenceOutputDirectory.OWNER_MARKER),
                "owner".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() ->
                DependencyEvidenceOutputDirectory.validate(
                        evidence.toString(), "wrong", source))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() ->
                DependencyEvidenceOutputDirectory.validate(
                        inside.toString(), "owner", source))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void rejectsSymlinkEscapeAndSymlinkOwnerMarker() throws Exception {
        final Path source = Files.createDirectory(
                temporaryDirectory.resolve("source"));
        final Path evidence = evidence("evidence", "owner");
        final Path sourceLink = source.resolve("cache-link");
        Files.createSymbolicLink(sourceLink, evidence);

        assertThatThrownBy(() ->
                DependencyEvidenceOutputDirectory.validate(
                        sourceLink.toString(), "owner", source))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("outside");

        final Path linkedMarkerDirectory = Files.createDirectory(
                temporaryDirectory.resolve("linked-marker"));
        Files.createSymbolicLink(linkedMarkerDirectory.resolve(
                        DependencyEvidenceOutputDirectory.OWNER_MARKER),
                evidence.resolve(
                        DependencyEvidenceOutputDirectory.OWNER_MARKER));
        assertThatThrownBy(() ->
                DependencyEvidenceOutputDirectory.validate(
                        linkedMarkerDirectory.toString(), "owner", source))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("marker is unavailable");
    }

    private Path evidence(
            final String name,
            final String owner) throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve(name));
        Files.write(directory.resolve(
                        DependencyEvidenceOutputDirectory.OWNER_MARKER),
                owner.getBytes(StandardCharsets.UTF_8));
        return directory;
    }
}
