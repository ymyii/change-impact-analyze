package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Artifact Path JSON Schema v1 parser tests. */
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

        assertThat(result.getModule()).isEqualTo(
                new ArtifactCoord("test", "module", "jar", "1"));
        assertThat(result.getBaseDirectory())
                .isEqualTo(temporaryDirectory.toRealPath());
        assertThat(result.getArtifacts()).singleElement()
                .satisfies(value -> {
                    assertThat(value.getArtifact()).isEqualTo(
                            new ArtifactCoord("g", "a", "test-jar",
                                    "2-SNAPSHOT", "tests"));
                    assertThat(value.getScope())
                            .isEqualTo(DependencyScope.RUNTIME);
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
                "\"schemaVersion\": 1,",
                "\"schemaVersion\": 1,\"schemaVersion\": 1,"));

        assertThatThrownBy(() -> ResolvedArtifactJsonParser.parse(json))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("Invalid resolved artifact JSON");
    }

    @Test
    void rejectsWrongSchemaAndDuplicateBinding() throws Exception {
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("artifact.jar"));
        final Path wrongSchema = write(manifest(null)
                .replace("\"schemaVersion\": 1",
                        "\"schemaVersion\": 2"));
        final String item = artifactItem(artifact);
        final Path duplicate = write(manifest(artifact)
                .replace("]}", "," + item + "]}"));

        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(wrongSchema))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("schemaVersion must equal 1");
        assertThatThrownBy(() ->
                ResolvedArtifactJsonParser.parse(duplicate))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("duplicate artifact binding");
    }

    private Path write(final String content) throws Exception {
        final Path json = temporaryDirectory.resolve(
                "manifest-" + System.nanoTime() + ".json");
        Files.writeString(json, content);
        return json;
    }

    private String manifest(final Path artifact) {
        return "{\"schemaVersion\": 1,"
                + "\"module\": {\"coordinates\": {"
                + "\"groupId\":\"test\",\"artifactId\":\"module\","
                + "\"type\":\"jar\",\"extension\":\"jar\","
                + "\"classifier\":\"\",\"version\":\"1\","
                + "\"baseVersion\":\"1\"},\"baseDirectory\":\""
                + jsonPath(temporaryDirectory) + "\"},"
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
                + "\"scope\":\"runtime\",\"absolutePath\":\""
                + jsonPath(artifact) + "\"}";
    }

    private String jsonPath(final Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
