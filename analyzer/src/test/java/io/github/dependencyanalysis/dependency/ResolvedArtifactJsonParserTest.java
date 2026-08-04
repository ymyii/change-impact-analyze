package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Artifact Path JSON Schema v2 parser tests. */
class ResolvedArtifactJsonParserTest {

    /** Temporary module directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void parsesClassifierUnicodeAndUnknownFields() throws Exception {
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("依赖 with space.jar"));
        final Path json = write(manifest(artifact)
                .replace("\"artifacts\": [", "\"future\": true,"
                        + "\"artifacts\": ["));

        final ResolvedArtifactManifest result =
                ResolvedArtifactJsonParser.parse(json);

        assertThat(result.getDirectory())
                .isEqualTo(temporaryDirectory.toRealPath());
        assertThat(result.getArtifacts()).singleElement()
                .satisfies(value -> {
                    assertThat(value.getArtifact()).isEqualTo(
                            new ArtifactCoord("g", "a", "test-jar",
                                    "2-SNAPSHOT", "tests"));
                    assertThat(value.getPath())
                            .isEqualTo(artifact.toRealPath());
                });
    }

    @Test
    void acceptsEmptyArtifactArray() throws Exception {
        final Path json = write(manifest(null));

        assertThat(ResolvedArtifactJsonParser.parse(json)
                .getArtifacts()).isEmpty();
    }

    @Test
    void rejectsDuplicateProperty() throws Exception {
        final Path json = write(manifest(null).replace(
                "\"schemaVersion\": 2,",
                "\"schemaVersion\": 2,\"schemaVersion\": 2,"));

        assertThatThrownBy(() -> ResolvedArtifactJsonParser.parse(json))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("Invalid resolved artifact JSON");
    }

    @Test
    void rejectsWrongSchemaAndDuplicateBinding() throws Exception {
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("artifact.jar"));
        final Path otherArtifact = Files.createFile(
                temporaryDirectory.resolve("other-artifact.jar"));
        final Path wrongSchema = write(manifest(null)
                .replace("\"schemaVersion\": 2",
                        "\"schemaVersion\": 1"));
        final String item = artifactItem(otherArtifact);
        final Path duplicate = write(manifest(artifact)
                .replace("]}", "," + item + "]}"));

        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(wrongSchema))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("schemaVersion must equal 2");
        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(duplicate))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("duplicate artifact binding");
    }

    @Test
    void rejectsMissingRelativeAndUnavailablePaths() throws Exception {
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("artifact.jar"));
        final String valid = manifest(artifact);
        final Path missingField = write(valid.replace(
                "\"absolutePath\"", "\"futurePath\""));
        final Path relative = write(valid.replace(
                jsonPath(artifact), "relative.jar"));
        final Path unavailable = write(valid.replace(
                jsonPath(artifact),
                jsonPath(temporaryDirectory.resolve("missing.jar"))));

        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(missingField))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining(
                        "artifact coordinates and absolutePath are required");
        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(relative))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("path must be absolute");
        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(unavailable))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining(
                        "path does not exist with expected type");
    }

    private Path write(final String content) throws Exception {
        final Path json = temporaryDirectory.resolve(
                "manifest-" + System.nanoTime() + ".json");
        Files.writeString(json, content);
        return json;
    }

    private String manifest(final Path artifact) {
        return "{\"schemaVersion\": 2,"
                + "\"artifacts\": ["
                + (artifact == null ? "" : artifactItem(artifact))
                + "]}";
    }

    private String artifactItem(final Path artifact) {
        return "{\"coordinates\": {\"groupId\":\"g\","
                + "\"artifactId\":\"a\",\"type\":\"test-jar\","
                + "\"extension\":\"jar\",\"classifier\":\"tests\","
                + "\"version\":\"2-20260804.010203-1\","
                + "\"baseVersion\":\"2-SNAPSHOT\"},"
                + "\"absolutePath\":\""
                + jsonPath(artifact) + "\"}";
    }

    private String jsonPath(final Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
