package io.github.dependencyanalysis.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Structural winner and raw occurrence normalization tests. */
class DependencyEvidenceGraphTest {

    /** Expected edges for two two-level paths. */
    private static final int TWO_PATH_EDGE_COUNT = 4;

    /** Mojo under test. */
    private CollectDependencyEvidenceMojo mojo;

    /** Module artifact. */
    private Artifact module;

    @BeforeEach
    void createMojo() throws Exception {
        mojo = new CollectDependencyEvidenceMojo();
        module = artifact("module", "1", null);
        final MavenProject project = new MavenProject();
        project.setArtifact(module);
        final Field field = CollectDependencyEvidenceMojo.class
                .getDeclaredField("project");
        field.setAccessible(true);
        field.set(mojo, project);
    }

    @Test
    void prunesRawBranchWithoutRetainedWinner() throws Exception {
        final DefaultDependencyNode selectedRoot = node(module);
        selectedRoot.setChildren(Collections.singletonList(
                node(artifact("parent", "1", "provided"))));
        final DefaultDependencyNode rawRoot = node(module);
        final DefaultDependencyNode parent = node(
                artifact("parent", "1", "provided"));
        parent.setChildren(Collections.singletonList(
                node(artifact("marker", "1", "compile"))));
        rawRoot.setChildren(Arrays.asList(parent,
                node(artifact("marker", "1", "test"))));

        final DependencyEvidenceModel.OccurrenceGraph graph =
                mojo.normalizeOccurrences(rawRoot,
                        mojo.selectedState(selectedRoot,
                                Collections.emptySet()));

        assertThat(graph.getOccurrences())
                .extracting(value -> value.getCoordinates()
                        .getArtifactId())
                .containsExactly("module", "parent")
                .doesNotContain("marker");
        assertThat(graph.getEdges()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.getParentId()).isEqualTo("root");
                    assertThat(edge.getChildId())
                            .isEqualTo("occurrence-0");
                });
    }

    @Test
    void prunesTestScopedReactorWinnerAndRetainedDuplicate()
            throws Exception {
        final Artifact testWinner = artifact(
                "reactor-marker", "1", "test");
        final DefaultDependencyNode selectedRoot = node(module);
        selectedRoot.setChildren(Collections.singletonList(
                node(testWinner)));
        final DefaultDependencyNode rawRoot = node(module);
        rawRoot.setChildren(Arrays.asList(
                node(testWinner),
                node(artifact("reactor-marker", "1", "provided"))));

        final DependencyEvidenceModel.OccurrenceGraph graph =
                mojo.normalizeOccurrences(rawRoot,
                        mojo.selectedState(selectedRoot,
                                Collections.singleton(
                                        ArtifactCoordinates.from(testWinner)
                                                .identity())));

        assertThat(graph.getOccurrences())
                .extracting(value -> value.getCoordinates()
                        .getArtifactId())
                .containsExactly("module");
        assertThat(graph.getEdges()).isEmpty();
    }

    @Test
    void normalizesMultipleLoserPathsToSelectedWinner() throws Exception {
        final DefaultDependencyNode selectedRoot = node(module);
        final DefaultDependencyNode branchA = node(
                artifact("branch-a", "1", "compile"));
        branchA.setChildren(Collections.singletonList(
                node(artifact("selected", "1", "compile"))));
        final DefaultDependencyNode branchB = node(
                artifact("branch-b", "1", "compile"));
        selectedRoot.setChildren(Arrays.asList(branchA, branchB));

        final DefaultDependencyNode rawRoot = node(module);
        final DefaultDependencyNode rawA = node(
                artifact("branch-a", "1", "compile"));
        rawA.setChildren(Collections.singletonList(
                node(artifact("selected", "1", "compile"))));
        final DefaultDependencyNode rawB = node(
                artifact("branch-b", "1", "compile"));
        rawB.setChildren(Collections.singletonList(
                node(artifact("selected", "2", "compile"))));
        rawRoot.setChildren(Arrays.asList(rawA, rawB));

        final DependencyEvidenceModel.OccurrenceGraph graph =
                mojo.normalizeOccurrences(rawRoot,
                        mojo.selectedState(selectedRoot,
                                Collections.emptySet()));

        assertThat(graph.getOccurrences())
                .filteredOn(value -> value.getCoordinates()
                        .getArtifactId().equals("selected"))
                .hasSize(2)
                .allSatisfy(value -> assertThat(
                        value.getCoordinates().getVersion())
                        .isEqualTo("1"));
        assertThat(graph.getEdges()).hasSize(TWO_PATH_EDGE_COUNT);
    }

    @Test
    void failsWhenRetainedWinnerHasNoRawOccurrence() throws Exception {
        final DefaultDependencyNode selectedRoot = node(module);
        selectedRoot.setChildren(Collections.singletonList(
                node(artifact("selected", "1", "compile"))));
        final DefaultDependencyNode rawRoot = node(module);
        final CollectDependencyEvidenceMojo.SelectedState selected =
                mojo.selectedState(selectedRoot, Collections.emptySet());

        assertThatThrownBy(() ->
                mojo.normalizeOccurrences(rawRoot, selected))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("missing selected winners")
                .hasMessageContaining("fixture:selected:jar");
    }

    private DefaultDependencyNode node(final Artifact artifact) {
        final DefaultDependencyNode node = new DefaultDependencyNode(artifact);
        node.setChildren(Collections.emptyList());
        return node;
    }

    private Artifact artifact(
            final String artifactId,
            final String version,
            final String scope) {
        return new DefaultArtifact("fixture", artifactId, version, scope,
                "jar", "", new DefaultArtifactHandler("jar"));
    }
}
