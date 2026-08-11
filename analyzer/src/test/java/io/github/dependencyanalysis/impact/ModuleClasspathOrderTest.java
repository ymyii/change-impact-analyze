package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests selected Maven classpath tier ordering. */
class ModuleClasspathOrderTest {

    /** Module-local evidence directory. */
    @TempDir
    private Path moduleDirectory;

    @Test
    void preservesSelectedTraversalInsteadOfCoordinateOrder()
            throws Exception {
        final ArtifactCoord module = artifact("module");
        final ArtifactCoord reactorZ = artifact("reactor-z");
        final ArtifactCoord reactorA = artifact("reactor-a");
        final ArtifactCoord externalZ = artifact("external-z");
        final ArtifactCoord externalA = artifact("external-a");
        final ResolvedArtifact z = new ResolvedArtifact(
                externalZ, moduleDirectory.resolve("z.jar"));
        final ResolvedArtifact a = new ResolvedArtifact(
                externalA, moduleDirectory.resolve("a.jar"));
        final ModuleDependencyOccurrenceGraph graph =
                new ModuleDependencyOccurrenceGraph("root", List.of(
                        new ModuleDependencyOccurrenceGraph.Occurrence(
                                "root", module, null, true, false)),
                        List.of());
        final ModuleDependencyEvidence evidence =
                new ModuleDependencyEvidence(module, moduleDirectory,
                        List.of(node(externalZ, List.of()),
                                node(externalA, List.of())), graph,
                        List.of(reactorZ.diffKey(), reactorA.diffKey()),
                        List.of(z, a));

        assertThat(ModuleClasspathOrder.reactorKeys(evidence))
                .containsExactly(reactorZ.diffKey(), reactorA.diffKey());
        assertThat(ModuleClasspathOrder.externalArtifacts(evidence))
                .containsExactly(z.getArtifact(), a.getArtifact());
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
