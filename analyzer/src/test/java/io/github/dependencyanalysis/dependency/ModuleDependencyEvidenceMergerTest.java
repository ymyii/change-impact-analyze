package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.DependencyPathEvidence;
import io.github.dependencyanalysis.impact.ModuleChangedPathSelection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests selected-winner and verbose-topology evidence merging. */
class ModuleDependencyEvidenceMergerTest {

    /** Module evidence directory. */
    @TempDir
    private Path directory;

    @Test
    void normalizesLoserOccurrencesAndRetainsEveryPath()
            throws Exception {
        final MergeFixture fixture = mediationFixture();

        final ModuleDependencyEvidence evidence = merge(fixture,
                bindings(fixture.selectedArtifacts())).get(0);

        assertThat(evidence.getOccurrenceGraph().occurrences())
                .extracting(ModuleDependencyOccurrenceGraph.Occurrence::id)
                .containsExactly("root", "a", "c", "winner", "x", "y",
                        "loser");
        assertThat(evidence.getOccurrenceGraph().edges())
                .hasSameSizeAs(fixture.verbose().getOccurrenceGraph()
                        .edges());
        assertThat(evidence.getOccurrenceGraph().occurrences())
                .filteredOn(value -> value.artifact().diffKey()
                        .equals(fixture.winner().diffKey()))
                .extracting(value -> value.artifact().getVersion())
                .containsExactly("2", "2");

        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(evidence,
                        Set.of(fixture.winner()),
                        DependencyAnalysisScopeMode.CHANGED_PATHS);
        assertThat(selection.paths())
                .extracting(DependencyPathEvidence::stablePath)
                .containsExactly(
                        artifact("path-a", "1") + " -> "
                                + artifact("path-c", "1") + " -> "
                                + fixture.winner(),
                        artifact("path-x", "1") + " -> "
                                + artifact("path-y", "1") + " -> "
                                + fixture.winner());
        assertThat(selection.selectedArtifacts())
                .contains(fixture.winner())
                .doesNotContain(fixture.loser());
        assertThatThrownBy(() -> selection.policyFor(fixture.loser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a selected target binding");
    }

    @Test
    void rejectsNonUniqueSelectedWinner() {
        final MergeFixture fixture = mediationFixture();
        final ArtifactCoord module = fixture.selected().getModule();
        final ArtifactCoord winnerOne = artifact("scenario-api", "1");
        final ArtifactCoord winnerTwo = artifact("scenario-api", "2");
        final ModuleDependencyTree selected = tree(module,
                List.of(leaf(winnerOne), leaf(winnerTwo)),
                graph(module, List.of(
                        occurrence("one", winnerOne),
                        occurrence("two", winnerTwo)),
                        List.of(edge("root", "one"),
                                edge("root", "two"))));

        assertThatThrownBy(() -> ModuleDependencyEvidenceMerger.merge(
                List.of(selected), List.of(fixture.verbose()),
                List.of(new ResolvedArtifactManifest(directory,
                        bindings(List.of(winnerTwo)))), Set.of()))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("winner is not unique");
    }

    @Test
    void rejectsVerboseOccurrenceWithoutWinner() {
        final MergeFixture fixture = mediationFixture();
        final ArtifactCoord missing = artifact("verbose-only", "1");
        final ModuleDependencyOccurrenceGraph original =
                fixture.verbose().getOccurrenceGraph();
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                new ArrayList<>(original.occurrences());
        nodes.add(occurrence("missing", missing));
        final List<ModuleDependencyOccurrenceGraph.Edge> edges =
                new ArrayList<>(original.edges());
        edges.add(edge("root", "missing"));
        final ModuleDependencyTree verbose = tree(
                fixture.verbose().getModule(),
                fixture.verbose().getDependencies(),
                new ModuleDependencyOccurrenceGraph(
                        "root", nodes, edges));

        assertThatThrownBy(() -> ModuleDependencyEvidenceMerger.merge(
                List.of(fixture.selected()), List.of(verbose),
                List.of(new ResolvedArtifactManifest(directory,
                        bindings(fixture.selectedArtifacts()))), Set.of()))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("has no selected winner");
    }

    @Test
    void rejectsModuleMismatchAndMissingBinding() {
        final MergeFixture fixture = mediationFixture();
        final ArtifactCoord otherModule = artifact("other-module", "1");
        final ModuleDependencyTree mismatched = tree(otherModule,
                fixture.verbose().getDependencies(),
                graph(otherModule, List.of(), List.of()));

        assertThatThrownBy(() -> ModuleDependencyEvidenceMerger.merge(
                List.of(fixture.selected()), List.of(mismatched),
                List.of(new ResolvedArtifactManifest(directory,
                        bindings(fixture.selectedArtifacts()))), Set.of()))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("Module differ");

        final List<ArtifactCoord> missingWinnerBinding =
                fixture.selectedArtifacts().stream()
                        .filter(value -> !value.equals(fixture.winner()))
                        .toList();
        assertThatThrownBy(() -> merge(fixture,
                bindings(missingWinnerBinding)))
                .isInstanceOf(DependencyAnalysisException.class)
                .hasMessageContaining("bindings differ")
                .hasMessageContaining(fixture.winner().toString());
    }

    @Test
    void omittedReactorCoordinateDoesNotBecomeSelectedReactor()
            throws Exception {
        final ArtifactCoord module = artifact("application", "1");
        final ArtifactCoord reactorLoser = artifact("reactor-lib", "1");
        final ArtifactCoord externalWinner = artifact("reactor-lib", "2");
        final ModuleDependencyTree selected = new ModuleDependencyTree(
                module, directory, List.of(leaf(externalWinner)));
        final ModuleDependencyTree verbose = tree(module,
                List.of(leaf(reactorLoser)), graph(module,
                        List.of(occurrence("omitted-reactor",
                                reactorLoser)),
                        List.of(edge("root", "omitted-reactor"))));

        final ModuleDependencyEvidence evidence =
                ModuleDependencyEvidenceMerger.merge(
                        List.of(selected), List.of(verbose),
                        List.of(new ResolvedArtifactManifest(directory,
                                bindings(List.of(externalWinner)))),
                        Set.of(reactorLoser)).get(0);

        assertThat(evidence.getSelectedReactorKeys()).isEmpty();
        assertThat(evidence.getArtifactCoordinates())
                .containsExactly(externalWinner);
        assertThat(evidence.getOccurrenceGraph()
                .occurrence("omitted-reactor"))
                .satisfies(value -> {
                    assertThat(value.artifact()).isEqualTo(externalWinner);
                    assertThat(value.reactor()).isFalse();
                });
    }

    private List<ModuleDependencyEvidence> merge(
            final MergeFixture fixture,
            final List<ResolvedArtifact> artifacts)
            throws DependencyAnalysisException {
        return ModuleDependencyEvidenceMerger.merge(
                List.of(fixture.selected()), List.of(fixture.verbose()),
                List.of(new ResolvedArtifactManifest(directory, artifacts)),
                Set.of());
    }

    private MergeFixture mediationFixture() {
        final ArtifactCoord module = artifact("application", "1");
        final ArtifactCoord pathA = artifact("path-a", "1");
        final ArtifactCoord pathC = artifact("path-c", "1");
        final ArtifactCoord pathX = artifact("path-x", "1");
        final ArtifactCoord pathY = artifact("path-y", "1");
        final ArtifactCoord winner = artifact("scenario-api", "2");
        final ArtifactCoord loser = artifact("scenario-api", "1");
        final List<DependencyNode> selectedDependencies = List.of(
                node(pathA, List.of(node(pathC, List.of(leaf(winner))))),
                node(pathX, List.of(node(pathY, List.of(leaf(winner))))));
        final ModuleDependencyTree selected = new ModuleDependencyTree(
                module, directory, selectedDependencies);
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                List.of(occurrence("a", pathA),
                        occurrence("c", pathC),
                        occurrence("winner", winner),
                        occurrence("x", pathX),
                        occurrence("y", pathY),
                        occurrence("loser", loser));
        final List<ModuleDependencyOccurrenceGraph.Edge> edges = List.of(
                edge("root", "a"), edge("a", "c"),
                edge("c", "winner"), edge("root", "x"),
                edge("x", "y"), edge("y", "loser"));
        final ModuleDependencyTree verbose = tree(module,
                selectedDependencies, graph(module, nodes, edges));
        return new MergeFixture(selected, verbose, winner, loser,
                List.of(pathA, pathC, winner, pathX, pathY));
    }

    private ModuleDependencyTree tree(
            final ArtifactCoord module,
            final List<DependencyNode> dependencies,
            final ModuleDependencyOccurrenceGraph graph) {
        return new ModuleDependencyTree(module, directory,
                dependencies, graph);
    }

    private ModuleDependencyOccurrenceGraph graph(
            final ArtifactCoord module,
            final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes,
            final List<ModuleDependencyOccurrenceGraph.Edge> edges) {
        final List<ModuleDependencyOccurrenceGraph.Occurrence> all =
                new ArrayList<>();
        all.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                "root", module, null, true, false));
        all.addAll(nodes);
        return new ModuleDependencyOccurrenceGraph("root", all, edges);
    }

    private ModuleDependencyOccurrenceGraph.Occurrence occurrence(
            final String id,
            final ArtifactCoord artifact) {
        return new ModuleDependencyOccurrenceGraph.Occurrence(
                id, artifact, DependencyScope.COMPILE, false, false);
    }

    private ModuleDependencyOccurrenceGraph.Edge edge(
            final String parent,
            final String child) {
        return new ModuleDependencyOccurrenceGraph.Edge(parent, child);
    }

    private DependencyNode leaf(final ArtifactCoord artifact) {
        return node(artifact, List.of());
    }

    private DependencyNode node(
            final ArtifactCoord artifact,
            final List<DependencyNode> children) {
        return new DependencyNode(
                artifact, DependencyScope.COMPILE, children);
    }

    private List<ResolvedArtifact> bindings(
            final List<ArtifactCoord> artifacts) {
        return DependencyEvidenceFixtures.bindings(directory, artifacts);
    }

    private ArtifactCoord artifact(
            final String artifactId,
            final String version) {
        return new ArtifactCoord("test", artifactId, "jar", version);
    }

    /**
     * Complete mediation merge fixture.
     *
     * @param selected ordinary selected tree
     * @param verbose verbose occurrence tree
     * @param winner selected coordinate
     * @param loser omitted coordinate
     * @param selectedArtifacts selected external coordinates
     */
    private record MergeFixture(
            ModuleDependencyTree selected,
            ModuleDependencyTree verbose,
            ArtifactCoord winner,
            ArtifactCoord loser,
            List<ArtifactCoord> selectedArtifacts) {
    }
}
