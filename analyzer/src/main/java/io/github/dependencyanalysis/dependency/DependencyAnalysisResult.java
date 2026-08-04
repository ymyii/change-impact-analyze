package io.github.dependencyanalysis.dependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dependency trees plus canonical physical artifact bindings. */
public final class DependencyAnalysisResult {

    /** Parsed dependency trees. */
    private final List<ModuleDependencyTree> trees;

    /** Flattened resolved physical artifacts. */
    private final List<ResolvedArtifact> artifacts;

    /** Resolved artifacts indexed by contextual manifest directory. */
    private final Map<Path, List<ResolvedArtifact>> artifactsByDirectory;

    /**
     * Creates a dependency analysis result.
     *
     * @param moduleTrees parsed module trees
     * @param resolvedArtifacts resolved artifacts by manifest directory
     */
    public DependencyAnalysisResult(
            final List<ModuleDependencyTree> moduleTrees,
            final Map<Path, List<ResolvedArtifact>> resolvedArtifacts) {
        trees = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(moduleTrees, "moduleTrees")));
        Objects.requireNonNull(resolvedArtifacts, "resolvedArtifacts");
        final Map<Path, List<ResolvedArtifact>> indexed =
                new LinkedHashMap<>();
        final List<ResolvedArtifact> flattened = new ArrayList<>();
        resolvedArtifacts.forEach((directory, values) -> {
            final Path normalized = canonicalDirectory(
                    Objects.requireNonNull(
                            directory, "artifactDirectory"));
            final List<ResolvedArtifact> copy = List.copyOf(
                    Objects.requireNonNull(values, "artifactValues"));
            if (indexed.put(normalized, copy) != null) {
                throw new IllegalArgumentException(
                        "Duplicate artifact directory: " + normalized);
            }
            flattened.addAll(copy);
        });
        artifactsByDirectory = Collections.unmodifiableMap(indexed);
        artifacts = Collections.unmodifiableList(flattened);
    }

    /** @return parsed module trees */
    public List<ModuleDependencyTree> getTrees() {
        return trees;
    }

    /** @return physical artifact bindings */
    public List<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    /**
     * Returns bindings owned by one module directory.
     *
     * @param modulePath absolute module directory
     * @return deterministic binding list
     */
    public List<ResolvedArtifact> artifactsFor(final Path modulePath) {
        final Path directory = canonicalDirectory(modulePath);
        return artifactsByDirectory.getOrDefault(directory, List.of());
    }

    private static Path canonicalDirectory(final Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException exception) {
            return directory.toAbsolutePath().normalize();
        }
    }
}
