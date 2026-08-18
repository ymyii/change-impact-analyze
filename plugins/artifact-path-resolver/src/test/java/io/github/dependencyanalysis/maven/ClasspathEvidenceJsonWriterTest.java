package io.github.dependencyanalysis.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classpath Evidence Schema v1 writer tests. */
class ClasspathEvidenceJsonWriterTest {

    /** Temporary evidence root. */
    @TempDir
    private Path temporary;

    @Test
    void writesStableOrderedSchemaAndPublishesAtomically() throws Exception {
        final Path module = Files.createDirectory(
                temporary.resolve("module")).toRealPath();
        final Path outputDirectory = Files.createDirectory(
                module.resolve("classes")).toRealPath();
        final ArtifactCoordinates coordinates = new ArtifactCoordinates(
                "demo", "app", "jar", "jar", "", "1", "1");
        final ClasspathEvidenceModel evidence = new ClasspathEvidenceModel(
                17, coordinates, module,
                Collections.singletonList(new ClasspathEvidenceModel.Entry(
                        0, "PROJECT", coordinates, "", outputDirectory)),
                Collections.singletonList("classifier unavailable"));
        final Path output = temporary.resolve("classpath-module-test.json");

        ClasspathEvidenceJsonWriter.write(output, evidence);

        assertThat(output).content()
                .contains("\"schemaVersion\" : 1")
                .contains("\"javaMajor\" : 17")
                .contains("\"order\" : 0")
                .contains("\"origin\" : \"PROJECT\"")
                .contains("\"absolutePath\" : \""
                        + escaped(outputDirectory))
                .contains("classifier unavailable");
        assertThatThrownBy(() ->
                ClasspathEvidenceJsonWriter.write(output, evidence))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("already exists");
    }

    private String escaped(final Path path) {
        return path.toString().replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
