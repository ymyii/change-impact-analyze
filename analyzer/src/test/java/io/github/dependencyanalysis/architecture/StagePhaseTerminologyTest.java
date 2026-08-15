package io.github.dependencyanalysis.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the project-owned Stage and Phase terminology. */
class StagePhaseTerminologyTest {

    /** Text file suffixes covered by the terminology gate. */
    private static final Set<String> TEXT_SUFFIXES = Set.of(
            ".java", ".md", ".tsv", ".txt", ".xml", ".properties",
            ".js", ".css", ".html");

    @Test
    void projectSourcesTestsAndWikiUseStageAndPhaseTerminology()
            throws IOException {
        final Path root = repositoryRoot();
        final String forbidden = "ta" + "sk";
        final List<String> forcedExternalNames = List.of(
                "begin" + "Ta" + "sk(",
                "sub" + "Ta" + "sk(",
                "get" + "Ta" + "skCount(",
                "getCompleted" + "Ta" + "skCount(",
                "compiler.get" + "Ta" + "sk(",
                "Future" + "Ta" + "sk",
                "ForkJoin" + "Ta" + "sk");
        final List<String> violations = new ArrayList<>();
        for (Path sourceRoot : sourceRoots(root)) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isTextFile)
                        .forEach(file -> inspect(root, file, forbidden,
                                forcedExternalNames, violations));
            }
        }

        assertThat(violations)
                .as("project-owned control-flow terminology violations")
                .isEmpty();
    }

    private List<Path> sourceRoots(final Path root) {
        return List.of(
                root.resolve("analyzer/src/main"),
                root.resolve("analyzer/src/test"),
                root.resolve("analyzer/src/integration-test"),
                root.resolve("models/jdk/src"),
                root.resolve("models/jdk8/src"),
                root.resolve("plugins/artifact-path-resolver/src"),
                root.resolve("wiki"),
                root.resolve("docs"));
    }

    private void inspect(
            final Path root,
            final Path file,
            final String forbidden,
            final List<String> forcedExternalNames,
            final List<String> violations) {
        try {
            final List<String> lines = Files.readAllLines(
                    file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                final String line = lines.get(index);
                if (containsIgnoreCase(line, forbidden)
                        && forcedExternalNames.stream().noneMatch(
                        value -> containsIgnoreCase(line, value))) {
                    violations.add(root.relativize(file) + ":"
                            + (index + 1) + ": " + line.trim());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect terminology in " + file, exception);
        }
    }

    private boolean isTextFile(final Path file) {
        final String name = file.getFileName().toString();
        return TEXT_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private boolean containsIgnoreCase(
            final String text,
            final String expected) {
        return text.toLowerCase(Locale.ROOT).contains(
                expected.toLowerCase(Locale.ROOT));
    }

    private Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("wiki"))
                    && Files.isDirectory(candidate.resolve("analyzer"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
