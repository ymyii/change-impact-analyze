package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.ModuleDependencyTree;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import java.util.List;

// Wiki: wiki/features/dependency-tree-extraction.md - Physical path lookup
/** Resolves one module-local physical artifact binding by coordinates. */
final class ArtifactPathBindingResolver {

    private ArtifactPathBindingResolver() {
    }

    static ResolvedArtifact require(
            final String side,
            final DependencyAnalysisResult analysis,
            final ModuleDependencyTree tree,
            final ArtifactCoord artifact,
            final String module) {
        if (tree == null) {
            throw new IllegalStateException(
                    "Dependency tree missing: side=" + side
                            + "; module=" + module);
        }
        final List<ResolvedArtifact> matches = analysis
                .artifactsFor(tree.getModulePath()).stream()
                .filter(value -> value.getArtifact().equals(artifact))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Artifact path binding must be unique: side=" + side
                            + "; module=" + module
                            + "; manifestDirectory="
                            + tree.getModulePath()
                            + "; artifact=" + artifact
                            + "; matches=" + matches.size());
        }
        return matches.get(0);
    }
}
