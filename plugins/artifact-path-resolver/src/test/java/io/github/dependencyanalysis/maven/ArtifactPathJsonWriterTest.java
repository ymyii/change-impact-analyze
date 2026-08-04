package io.github.dependencyanalysis.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** Artifact Path JSON Schema v2 writer tests. */
class ArtifactPathJsonWriterTest {

    /** Temporary output directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void writesOnlySchemaAndPhysicalArtifactBindings() throws Exception {
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("依赖 with space.jar"));
        final Path output = temporaryDirectory.resolve("artifacts.json");
        final ArtifactCoordinates coordinates = new ArtifactCoordinates(
                "g", "a", "test-jar", "jar", "tests",
                "2-20260804.010203-1", "2-SNAPSHOT");

        ArtifactPathJsonWriter.write(output, Collections.singletonList(
                new ResolvedArtifactPath(coordinates, artifact)));

        final String json = new String(Files.readAllBytes(output),
                StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"schemaVersion\" : 2")
                .contains("\"baseVersion\" : \"2-SNAPSHOT\"")
                .contains("\"absolutePath\" : \"" + escaped(artifact))
                .doesNotContain("\"module\"")
                .doesNotContain("\"scope\"");
        assertThat(temporaryDirectory)
                .isDirectoryContaining(value -> value.getFileName()
                        .toString().equals("artifacts.json"));
    }

    private String escaped(final Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
