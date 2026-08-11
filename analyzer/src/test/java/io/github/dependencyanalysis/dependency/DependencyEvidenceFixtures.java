package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test-only builders for merged Module dependency evidence. */
public final class DependencyEvidenceFixtures {

    private DependencyEvidenceFixtures() {
    }

    /**
     * Creates evidence with an explicit occurrence graph.
     *
     * @param directory Module directory
     * @param module Module coordinate
     * @param dependencies selected dependency projection
     * @param graph normalized occurrence graph
     * @param reactorKeys selected reactor keys
     * @param artifacts selected external bindings
     * @return test evidence
     */
    public static ModuleDependencyEvidence evidence(
            final Path directory,
            final ArtifactCoord module,
            final List<DependencyNode> dependencies,
            final ModuleDependencyOccurrenceGraph graph,
            final List<String> reactorKeys,
            final List<ResolvedArtifact> artifacts) {
        return new ModuleDependencyEvidence(module, directory,
                dependencies, graph, reactorKeys, artifacts);
    }

    /**
     * Creates dependency evidence sufficient for selected projection tests.
     *
     * @param directory Module directory
     * @param module Module coordinate
     * @param dependencies selected dependency projection
     * @return test evidence
     */
    public static ModuleDependencyEvidence selected(
            final Path directory,
            final ArtifactCoord module,
            final List<DependencyNode> dependencies) {
        final ModuleDependencyTree tree = new ModuleDependencyTree(
                module, directory, dependencies);
        return new ModuleDependencyEvidence(module, directory,
                dependencies, tree.getOccurrenceGraph(), List.of(),
                List.of());
    }

    /**
     * Creates synthetic physical bindings for selected artifacts.
     *
     * @param directory binding directory
     * @param artifacts selected coordinates
     * @return bindings
     */
    public static List<ResolvedArtifact> bindings(
            final Path directory,
            final List<ArtifactCoord> artifacts) {
        final List<ResolvedArtifact> result = new ArrayList<>();
        for (ArtifactCoord artifact : artifacts) {
            result.add(new ResolvedArtifact(artifact,
                    directory.resolve(artifact.getArtifactId() + ".jar")));
        }
        return List.copyOf(result);
    }
}
