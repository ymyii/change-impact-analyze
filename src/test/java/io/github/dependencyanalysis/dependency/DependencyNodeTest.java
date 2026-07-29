package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link DependencyNode}.
 */
class DependencyNodeTest {

    @Test
    void gettersReturnValues() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyNode node =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        assertThat(node.getArtifact())
                .isEqualTo(coord);
        assertThat(node.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
        assertThat(node.getChildren())
                .isEmpty();
    }

    @Test
    void childrenAreUnmodifiable() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyNode node =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        org.junit.jupiter.api.Assertions
                .assertThrows(
                        UnsupportedOperationException
                                .class,
                        () -> node.getChildren()
                                .add(null));
    }

    @Test
    void equalsAndHashCode() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyNode a =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        final DependencyNode b =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
    }

    @Test
    void notEqualDifferentScope() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyNode a =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        final DependencyNode b =
                new DependencyNode(
                        coord,
                        DependencyScope.RUNTIME,
                        List.of());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toStringContainsFields() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyNode node =
                new DependencyNode(
                        coord,
                        DependencyScope.COMPILE,
                        List.of());
        final String str = node.toString();
        assertThat(str).contains("g:a:jar:1.0");
        assertThat(str).contains("COMPILE");
    }

    @Test
    void nestedChildren() {
        final ArtifactCoord parentCoord =
                new ArtifactCoord(
                        "g", "parent", "jar",
                        "1.0");
        final ArtifactCoord childCoord =
                new ArtifactCoord(
                        "g", "child", "jar",
                        "1.0");
        final DependencyNode child =
                new DependencyNode(
                        childCoord,
                        DependencyScope.COMPILE,
                        List.of());
        final DependencyNode parent =
                new DependencyNode(
                        parentCoord,
                        DependencyScope.COMPILE,
                        List.of(child));
        assertThat(parent.getChildren())
                .hasSize(1);
        assertThat(parent.getChildren()
                .get(0))
                .isEqualTo(child);
    }
}
