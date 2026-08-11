package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Strict Dependency Evidence Schema v3 parser tests. */
class DependencyEvidenceJsonParserTest {

    /** Temporary fixture root. */
    @TempDir
    private Path temporaryDirectory;

    /** Canonical Module directory. */
    private Path moduleDirectory;

    /** Canonical dependency artifact. */
    private Path artifactPath;

    @BeforeEach
    void createPaths() throws Exception {
        moduleDirectory = Files.createDirectory(
                temporaryDirectory.resolve("Module 中文")).toRealPath();
        artifactPath = Files.createFile(
                temporaryDirectory.resolve("依赖 with space.jar"))
                .toRealPath();
    }

    @Test
    void parsesCompleteSchemaAndUsesLogicalVersion() throws Exception {
        final ModuleDependencyEvidence evidence =
                DependencyEvidenceJsonParser.parse(write(validJson()));

        assertThat(evidence.getModule().getArtifactId())
                .isEqualTo("module");
        assertThat(evidence.getModulePath()).isEqualTo(moduleDirectory);
        assertThat(evidence.getDependencies()).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getArtifact().getVersion())
                            .isEqualTo("2-SNAPSHOT");
                    assertThat(dependency.getScope())
                            .isEqualTo(DependencyScope.RUNTIME);
                });
        assertThat(evidence.getOccurrenceGraph().validate().valid())
                .isTrue();
        assertThat(evidence.getSelectedReactorKeys())
                .containsExactly("fixture:reactor:jar");
        assertThat(evidence.getArtifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact.getPath())
                        .isEqualTo(artifactPath));
    }

    @Test
    void rejectsUnknownDuplicateAndWrongSchemaFields() throws Exception {
        assertThatThrownBy(() -> DependencyEvidenceJsonParser.parse(write(
                validJson().replace("\"schemaVersion\": 3,",
                        "\"schemaVersion\": 3, \"unknown\": true,"))))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("unknown document property");
        assertThatThrownBy(() -> DependencyEvidenceJsonParser.parse(write(
                validJson().replace("\"schemaVersion\": 3,",
                        "\"schemaVersion\": 3, \"schemaVersion\": 3,"))))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("Invalid dependency evidence JSON");
        assertThatThrownBy(() -> DependencyEvidenceJsonParser.parse(write(
                validJson().replace("\"schemaVersion\": 3",
                        "\"schemaVersion\": 2"))))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("schemaVersion must equal 3");
    }

    @Test
    void rejectsRelativePathsAndUnreachableOccurrence() throws Exception {
        assertThatThrownBy(() -> DependencyEvidenceJsonParser.parse(write(
                validJson().replace(escaped(moduleDirectory),
                        "relative/module"))))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("path must be absolute");
        assertThatThrownBy(() -> DependencyEvidenceJsonParser.parse(write(
                validJson().replace("{\"parentId\": \"root\", "
                                + "\"childId\": \"occurrence-0\"}",
                        ""))))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining(
                        "UNREACHABLE_DEPENDENCY_OCCURRENCE");
    }

    private Path write(final String json) throws Exception {
        final Path file = temporaryDirectory.resolve(
                "evidence-" + System.nanoTime() + ".json");
        Files.writeString(file, json);
        return file;
    }

    private String validJson() {
        return """
                {
                  "schemaVersion": 3,
                  "module": %s,
                  "moduleDirectory": "%s",
                  "dependencies": [
                    {
                      "coordinates": %s,
                      "scope": "runtime",
                      "children": []
                    }
                  ],
                  "occurrenceGraph": {
                    "rootId": "root",
                    "occurrences": [
                      {
                        "id": "root",
                        "coordinates": %s,
                        "scope": "",
                        "moduleRoot": true,
                        "reactor": false
                      },
                      {
                        "id": "occurrence-0",
                        "coordinates": %s,
                        "scope": "runtime",
                        "moduleRoot": false,
                        "reactor": false
                      }
                    ],
                    "edges": [
                      {"parentId": "root", "childId": "occurrence-0"}
                    ]
                  },
                  "selectedReactorKeys": ["fixture:reactor:jar"],
                  "artifacts": [
                    {
                      "coordinates": %s,
                      "absolutePath": "%s"
                    }
                  ]
                }
                """.formatted(
                coordinates("module", "1", "1"),
                escaped(moduleDirectory),
                coordinates("dependency", "2-20260804.010203-1",
                        "2-SNAPSHOT"),
                coordinates("module", "1", "1"),
                coordinates("dependency", "2-20260804.010203-1",
                        "2-SNAPSHOT"),
                coordinates("dependency", "2-20260804.010203-1",
                        "2-SNAPSHOT"),
                escaped(artifactPath));
    }

    private String coordinates(
            final String artifactId,
            final String version,
            final String baseVersion) {
        return """
                {
                  "groupId": "fixture",
                  "artifactId": "%s",
                  "type": "jar",
                  "extension": "jar",
                  "classifier": "",
                  "version": "%s",
                  "baseVersion": "%s"
                }
                """.formatted(artifactId, version, baseVersion).trim();
    }

    private String escaped(final Path path) {
        return path.toString().replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
