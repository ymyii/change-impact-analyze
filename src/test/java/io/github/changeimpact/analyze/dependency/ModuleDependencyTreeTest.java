package io.github.changeimpact.analyze.dependency;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link ModuleDependencyTree}.
 */
class ModuleDependencyTreeTest {

    @Test
    void gettersReturnValues() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "g", "m", "jar",
                        "1.0");
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree tree =
                new ModuleDependencyTree(
                        mod, path,
                        List.of());
        assertThat(tree.getModule())
                .isEqualTo(mod);
        assertThat(tree.getModulePath())
                .isEqualTo(path);
        assertThat(tree.getDependencies())
                .isEmpty();
    }

    @Test
    void dependenciesAreUnmodifiable() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "g", "m", "jar",
                        "1.0");
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree tree =
                new ModuleDependencyTree(
                        mod, path,
                        List.of());
        org.junit.jupiter.api.Assertions
                .assertThrows(
                        UnsupportedOperationException
                                .class,
                        () -> tree
                                .getDependencies()
                                .add(null));
    }

    @Test
    void equalsAndHashCode() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "g", "m", "jar",
                        "1.0");
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree a =
                new ModuleDependencyTree(
                        mod, path,
                        List.of());
        final ModuleDependencyTree b =
                new ModuleDependencyTree(
                        mod, path,
                        List.of());
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
    }

    @Test
    void notEqualDifferentModule() {
        final ArtifactCoord mod1 =
                new ArtifactCoord(
                        "g", "m1", "jar",
                        "1.0");
        final ArtifactCoord mod2 =
                new ArtifactCoord(
                        "g", "m2", "jar",
                        "1.0");
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree a =
                new ModuleDependencyTree(
                        mod1, path,
                        List.of());
        final ModuleDependencyTree b =
                new ModuleDependencyTree(
                        mod2, path,
                        List.of());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toStringContainsFields() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "g", "m", "jar",
                        "1.0");
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree tree =
                new ModuleDependencyTree(
                        mod, path,
                        List.of());
        final String str = tree.toString();
        assertThat(str)
                .contains("g:m:jar:1.0");
        assertThat(str)
                .contains("/ws/mod");
    }

    @Test
    void withDependencies() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "g", "m", "jar",
                        "1.0");
        final ArtifactCoord depCoord =
                new ArtifactCoord(
                        "g", "dep", "jar",
                        "1.0");
        final DependencyNode dep =
                new DependencyNode(
                        depCoord,
                        DependencyScope.COMPILE,
                        List.of());
        final Path path =
                Paths.get("/ws/mod");
        final ModuleDependencyTree tree =
                new ModuleDependencyTree(
                        mod, path,
                        List.of(dep));
        assertThat(tree.getDependencies())
                .hasSize(1);
        assertThat(tree.getDependencies()
                .get(0))
                .isEqualTo(dep);
    }
}
