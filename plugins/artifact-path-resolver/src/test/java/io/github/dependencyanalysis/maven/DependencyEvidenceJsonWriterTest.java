package io.github.dependencyanalysis.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dependency Evidence Schema v3 writer tests. */
class DependencyEvidenceJsonWriterTest {

    /** Temporary output directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void atomicallyWritesCompleteSchemaAndRefusesDuplicatePublication()
            throws Exception {
        final Path module = Files.createDirectory(
                temporaryDirectory.resolve("Module 中文")).toRealPath();
        final Path artifact = Files.createFile(
                temporaryDirectory.resolve("依赖 with space.jar"))
                .toRealPath();
        final ArtifactCoordinates moduleCoordinates = coordinates(
                "fixture", "module", "1");
        final ArtifactCoordinates dependencyCoordinates = coordinates(
                "fixture", "dependency", "2-SNAPSHOT");
        final DependencyEvidenceModel evidence =
                new DependencyEvidenceModel(moduleCoordinates, module,
                        Collections.singletonList(
                                new DependencyEvidenceModel
                                        .SelectedDependency(
                                        dependencyCoordinates, "runtime",
                                        Collections.emptyList())),
                        new DependencyEvidenceModel.OccurrenceGraph(
                                "root", java.util.Arrays.asList(
                                new DependencyEvidenceModel.Occurrence(
                                        "root", moduleCoordinates, "",
                                        true, false),
                                new DependencyEvidenceModel.Occurrence(
                                        "occurrence-0",
                                        dependencyCoordinates, "runtime",
                                        false, false)),
                                Collections.singletonList(
                                        new DependencyEvidenceModel.Edge(
                                                "root", "occurrence-0"))),
                        Collections.singletonList(
                                "fixture:reactor:jar"),
                        Collections.singletonList(
                                new ResolvedArtifactPath(
                                        dependencyCoordinates, artifact)));
        final Path output = temporaryDirectory.resolve("module-test.json");

        DependencyEvidenceJsonWriter.write(output, evidence);

        final String json = new String(Files.readAllBytes(output),
                StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"schemaVersion\" : 3")
                .contains("\"moduleDirectory\" : \""
                        + escaped(module))
                .contains("\"occurrenceGraph\"")
                .contains("\"selectedReactorKeys\"")
                .contains("\"absolutePath\" : \""
                        + escaped(artifact));
        assertThatThrownBy(() ->
                DependencyEvidenceJsonWriter.write(output, evidence))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("already exists");
        try (java.util.stream.Stream<Path> files =
                     Files.list(temporaryDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toList()))
                    .noneMatch(name -> name.contains(".tmp-"));
        }
    }

    private ArtifactCoordinates coordinates(
            final String group,
            final String artifact,
            final String version) {
        return new ArtifactCoordinates(group, artifact, "jar", "jar", "",
                version, version);
    }

    private String escaped(final Path path) {
        return path.toString().replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
