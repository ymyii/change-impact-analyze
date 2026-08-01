package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Dependency trees plus canonical physical artifact bindings. */
public final class DependencyAnalysisResult {

    /** Parsed dependency trees. */
    private final List<ModuleDependencyTree> trees;

    /** Resolved physical artifacts. */
    private final List<ResolvedArtifact> artifacts;

    /**
     * Creates a dependency analysis result.
     *
     * @param moduleTrees parsed module trees
     * @param resolvedArtifacts resolved artifacts
     */
    public DependencyAnalysisResult(
            final List<ModuleDependencyTree> moduleTrees,
            final List<ResolvedArtifact> resolvedArtifacts) {
        trees = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(moduleTrees, "moduleTrees")));
        artifacts = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(resolvedArtifacts,
                        "resolvedArtifacts")));
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
        final Path normalized = modulePath.toAbsolutePath().normalize();
        return artifacts.stream()
                .filter(item -> item.getModulePath().equals(normalized))
                .toList();
    }
}
