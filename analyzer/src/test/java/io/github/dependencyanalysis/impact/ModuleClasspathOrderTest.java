package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyTree;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests GraphML-derived classpath tier ordering. */
class ModuleClasspathOrderTest {

    /** Module-local evidence directory. */
    @TempDir
    private Path moduleDirectory;

    @Test
    void preservesTraversalInsteadOfCoordinateOrder() {
        final ArtifactCoord module = artifact("module");
        final ArtifactCoord reactorZ = artifact("reactor-z");
        final ArtifactCoord reactorA = artifact("reactor-a");
        final ArtifactCoord externalZ = artifact("external-z");
        final ArtifactCoord externalA = artifact("external-a");
        final ModuleDependencyTree tree = new ModuleDependencyTree(
                module, moduleDirectory, List.of(
                node(reactorZ, List.of(node(externalZ, List.of()))),
                node(reactorA, List.of()),
                node(externalA, List.of())));
        final ResolvedArtifact z = new ResolvedArtifact(
                externalZ, moduleDirectory.resolve("z.jar"));
        final ResolvedArtifact a = new ResolvedArtifact(
                externalA, moduleDirectory.resolve("a.jar"));
        final Map<Path, List<ResolvedArtifact>> bindings =
                new LinkedHashMap<>();
        bindings.put(moduleDirectory, List.of(a, z));
        final DependencyAnalysisResult analysis =
                new DependencyAnalysisResult(List.of(tree), bindings);

        assertThat(ModuleClasspathOrder.reactorKeys(
                tree, Set.of(reactorA, reactorZ)))
                .containsExactly(reactorZ.diffKey(), reactorA.diffKey());
        assertThat(ModuleClasspathOrder.externalArtifacts(
                analysis, tree, Set.of(reactorA, reactorZ)))
                .containsExactly(z, a);
    }

    private DependencyNode node(
            final ArtifactCoord artifact,
            final List<DependencyNode> children) {
        return new DependencyNode(
                artifact, DependencyScope.COMPILE, children);
    }

    private ArtifactCoord artifact(final String artifactId) {
        return new ArtifactCoord("example", artifactId, "jar", "1");
    }
}
