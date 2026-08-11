package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyEvidenceFixtures;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests occurrence-preserving reverse dependency path selection. */
class ModuleChangedPathSelectionTest {

    @Test
    void selectsUnionOfAllPathsWithoutSiblingOrSeedDescendant() {
        final GraphFixture fixture = fixture();

        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(
                        fixture.evidence(), Set.of(fixture.seed()),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);

        assertThat(selection.actualMode()).isEqualTo(
                DependencyAnalysisScopeMode.CHANGED_PATHS);
        assertThat(selection.paths()).extracting(
                        DependencyPathEvidence::stablePath)
                .containsExactly(
                        coord("path-a") + " -> " + coord("path-c")
                                + " -> " + fixture.seed(),
                        coord("path-x") + " -> " + coord("path-y")
                                + " -> " + fixture.seed());
        assertThat(selection.selectedArtifacts()).extracting(
                        ArtifactCoord::getArtifactId)
                .containsExactlyInAnyOrder(
                        "path-a", "path-c", "scenario-api",
                        "path-x", "path-y")
                .doesNotContain("sibling", "seed-downstream");
    }

    @Test
    void retainsAllOccurrencesAndPathEvidenceForOneLogicalArtifact() {
        final GraphFixture fixture = fixture();

        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(
                        fixture.evidence(), Set.of(fixture.seed()),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);

        assertThat(selection.paths()).hasSize(2)
                .allSatisfy(path -> assertThat(path.seed())
                        .isEqualTo(fixture.seed()));
    }

    @Test
    void reactorOccurrenceParticipatesButIsNotExternalArtifactPolicy() {
        final ArtifactCoord module = coord("application");
        final ArtifactCoord reactor = coord("reactor-path");
        final ArtifactCoord seed = coord("scenario-api");
        final ModuleDependencyOccurrenceGraph graph = graph(
                List.of(node("root", module, true, false),
                        node("reactor", reactor, false, true),
                        node("seed", seed, false, false)),
                List.of(edge("root", "reactor"),
                        edge("reactor", "seed")));

        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(evidence(module, graph,
                                List.of(seed), List.of(reactor.diffKey())),
                        Set.of(seed),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);

        assertThat(selection.paths()).singleElement().satisfies(path ->
                assertThat(path.occurrences()).extracting(
                                value -> value.artifact().getArtifactId())
                        .containsExactly("reactor-path", "scenario-api"));
        assertThat(selection.selectedArtifacts()).containsExactly(seed);
    }

    @Test
    void missingSeedAndInvalidGraphFallbackToFull() {
        final GraphFixture fixture = fixture();
        final ModuleChangedPathSelection missing =
                ModuleChangedPathSelection.plan(
                        fixture.evidence(), Set.of(coord("missing")),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);
        assertThat(missing.actualMode()).isEqualTo(
                DependencyAnalysisScopeMode.FULL);
        assertThat(missing.fallbackReason()).hasValueSatisfying(reason ->
                assertThat(reason).startsWith(
                        "CHANGED_DEPENDENCY_SEED_MISSING"));

        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                List.of(node("root", coord("application"), true, false),
                        node("a", coord("a"), false, false));
        final ModuleDependencyOccurrenceGraph cyclic = graph(nodes,
                List.of(edge("root", "a"), edge("a", "root")));
        final ModuleChangedPathSelection invalid =
                ModuleChangedPathSelection.plan(evidence(
                                coord("application"), cyclic,
                                List.of(coord("a")), List.of()),
                        Set.of(coord("a")),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);
        assertThat(invalid.actualMode()).isEqualTo(
                DependencyAnalysisScopeMode.FULL);
        assertThat(invalid.fallbackReason()).contains(
                "DEPENDENCY_GRAPH_CYCLE");
        assertThat(invalid.selectedArtifacts())
                .containsExactly(coord("a"));
    }

    @Test
    void explicitFullSelectsEveryExternalArtifact() {
        final GraphFixture fixture = fixture();

        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(
                        fixture.evidence(), Set.of(fixture.seed()),
                        DependencyAnalysisScopeMode.FULL);

        assertThat(selection.actualMode()).isEqualTo(
                DependencyAnalysisScopeMode.FULL);
        assertThat(selection.selectedArtifacts()).extracting(
                        ArtifactCoord::getArtifactId)
                .contains("sibling", "seed-downstream");
        assertThat(selection.paths()).isEmpty();
    }

    private GraphFixture fixture() {
        final ArtifactCoord seed = coord("scenario-api");
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                new ArrayList<>(List.of(
                node("root", coord("application"), true, false),
                node("a", coord("path-a"), false, false),
                node("c", coord("path-c"), false, false),
                node("e", seed, false, false),
                node("d", coord("sibling"), false, false),
                node("g", coord("seed-downstream"), false, false),
                node("x", coord("path-x"), false, false),
                node("y", coord("path-y"), false, false)));
        final List<ModuleDependencyOccurrenceGraph.Edge> edges = List.of(
                edge("root", "a"), edge("a", "c"), edge("c", "e"),
                edge("a", "d"), edge("e", "g"), edge("root", "x"),
                edge("x", "y"), edge("y", "e"));
        final ModuleDependencyOccurrenceGraph graph = graph(nodes, edges);
        final List<ArtifactCoord> artifacts = nodes.stream()
                .filter(value -> !value.moduleRoot() && !value.reactor())
                .map(ModuleDependencyOccurrenceGraph.Occurrence::artifact)
                .distinct().toList();
        return new GraphFixture(evidence(coord("application"), graph,
                artifacts, List.of()), seed);
    }

    private ModuleDependencyEvidence evidence(
            final ArtifactCoord module,
            final ModuleDependencyOccurrenceGraph graph,
            final List<ArtifactCoord> artifacts,
            final List<String> reactorKeys) {
        final List<DependencyNode> dependencies = artifacts.stream()
                .map(value -> new DependencyNode(value,
                        DependencyScope.COMPILE, List.of()))
                .toList();
        return DependencyEvidenceFixtures.evidence(Path.of("/module"),
                module, dependencies, graph, reactorKeys,
                DependencyEvidenceFixtures.bindings(
                        Path.of("/bindings"), artifacts));
    }

    private ModuleDependencyOccurrenceGraph graph(
            final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes,
            final List<ModuleDependencyOccurrenceGraph.Edge> edges) {
        return new ModuleDependencyOccurrenceGraph("root", nodes, edges);
    }

    private ModuleDependencyOccurrenceGraph.Occurrence node(
            final String id,
            final ArtifactCoord artifact,
            final boolean root,
            final boolean reactor) {
        return new ModuleDependencyOccurrenceGraph.Occurrence(
                id, artifact, root ? null : DependencyScope.COMPILE,
                root, reactor);
    }

    private ModuleDependencyOccurrenceGraph.Edge edge(
            final String parent, final String child) {
        return new ModuleDependencyOccurrenceGraph.Edge(parent, child);
    }

    private ArtifactCoord coord(final String artifact) {
        return new ArtifactCoord("test", artifact, "jar", "1");
    }

    /**
     * @param evidence merged dependency evidence
     * @param seed changed artifact
     */
    private record GraphFixture(
            ModuleDependencyEvidence evidence,
            ArtifactCoord seed) {
    }
}
