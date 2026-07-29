package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link GraphMLParser}.
 */
class GraphMLParserTest {

    /** Temporary directory for tests. */
    @TempDir
    private Path tempDir;

    @Test
    void parseSingleModuleGraphML()
            throws Exception {
        final Path graphml =
                copyResource(
                        "single-module.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        assertThat(tree.getModule())
                .isEqualTo(
                        new ArtifactCoord(
                                "com.example",
                                "my-app",
                                "jar",
                                "1.0.0"));
        final List<DependencyNode> deps =
                tree.getDependencies();
        assertThat(deps)
                .extracting(n -> n
                        .getArtifact()
                        .getArtifactId())
                .containsExactlyInAnyOrder(
                        "slf4j-api",
                        "guava",
                        "javax.inject");
    }

    @Test
    void testScopeIsFiltered()
            throws Exception {
        final Path graphml =
                copyResource(
                        "single-module.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        assertThat(tree.getDependencies())
                .extracting(n -> n
                        .getArtifact()
                        .getArtifactId())
                .doesNotContain("junit");
    }

    @Test
    void reactorModulesFiltered()
            throws Exception {
        final Path graphml =
                copyResource(
                        "multi-module-with-reactor"
                                + ".graphml");
        final ArtifactCoord modB =
                new ArtifactCoord(
                        "com.example",
                        "mod-b",
                        "jar",
                        "1.0.0");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml,
                        Set.of(modB));
        assertThat(tree.getDependencies())
                .extracting(n -> n
                        .getArtifact()
                        .getArtifactId())
                .doesNotContain("mod-b")
                .contains("slf4j-api");
    }

    @Test
    void emptyDependenciesParsed()
            throws Exception {
        final Path graphml =
                copyResource(
                        "empty-deps.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        assertThat(tree.getDependencies())
                .isEmpty();
        assertThat(tree.getModule()
                .getArtifactId())
                .isEqualTo("empty");
    }

    @Test
    void scopesAreCorrect()
            throws Exception {
        final Path graphml =
                copyResource(
                        "single-module.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        final DependencyNode slf4j =
                findNode(tree, "slf4j-api");
        final DependencyNode guava =
                findNode(tree, "guava");
        final DependencyNode inject =
                findNode(tree, "javax.inject");
        assertThat(slf4j.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
        assertThat(guava.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
        assertThat(inject.getScope())
                .isEqualTo(DependencyScope
                        .PROVIDED);
    }

    @Test
    void transitiveDependencyParsed()
            throws Exception {
        final Path graphml =
                copyResource(
                        "single-module.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        final DependencyNode guava =
                findNode(tree, "guava");
        assertThat(guava.getChildren())
                .hasSize(1);
        assertThat(guava.getChildren()
                .get(0)
                .getArtifact()
                .getArtifactId())
                .isEqualTo(
                        "failureaccess");
        assertThat(guava.getChildren()
                .get(0).getScope())
                .isEqualTo(DependencyScope
                        .RUNTIME);
    }

    @Test
    void invalidXmlThrowsException()
            throws Exception {
        final Path bad = tempDir.resolve(
                "bad.graphml");
        Files.writeString(bad,
                "not valid xml content");
        assertThatThrownBy(() ->
                GraphMLParser.parse(
                        bad, Set.of()))
                .isInstanceOf(
                        DependencyAnalysisException
                                .class);
    }

    @Test
    void nonExistentFileThrowsException() {
        final Path missing =
                tempDir.resolve("no.graphml");
        assertThatThrownBy(() ->
                GraphMLParser.parse(
                        missing, Set.of()))
                .isInstanceOf(
                        DependencyAnalysisException
                                .class);
    }

    @Test
    void modulePathIsParentOfGraphML()
            throws Exception {
        final Path graphml =
                copyResource(
                        "single-module.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        assertThat(tree.getModulePath())
                .isEqualTo(graphml.getParent());
    }

    @Test
    void parseClassifierDependency()
            throws Exception {
        final Path graphml =
                copyResource(
                        "with-classifier.graphml");
        final ModuleDependencyTree tree =
                GraphMLParser.parse(
                        graphml, Set.of());
        assertThat(tree.getDependencies())
                .hasSize(1);
        final DependencyNode epoll =
                tree.getDependencies().get(0);
        assertThat(epoll.getArtifact()
                .getGroupId())
                .isEqualTo("io.netty");
        assertThat(epoll.getArtifact()
                .getArtifactId())
                .isEqualTo(
                        "netty-transport"
                                + "-native-epoll");
        assertThat(epoll.getArtifact()
                .getClassifier())
                .isEqualTo("linux-x86_64");
        assertThat(epoll.getArtifact()
                .getVersion())
                .isEqualTo("4.1.118.Final");
        assertThat(epoll.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
    }

    /**
     * Copies a test resource to temp dir.
     *
     * @param name resource file name
     * @return path to copied file
     * @throws Exception if copy fails
     */
    private Path copyResource(
            final String name)
            throws Exception {
        final Path target =
                tempDir.resolve(name);
        Files.copy(
                getClass().getResourceAsStream(
                        "/dependency/" + name),
                target);
        return target;
    }

    /**
     * Finds a dependency node by
     * artifactId in the tree.
     *
     * @param tree module tree
     * @param artifactId artifact id
     * @return matching node
     */
    private DependencyNode findNode(
            final ModuleDependencyTree tree,
            final String artifactId) {
        for (DependencyNode dep
                : tree.getDependencies()) {
            if (dep.getArtifact()
                    .getArtifactId()
                    .equals(artifactId)) {
                return dep;
            }
            final DependencyNode found =
                    findInChildren(dep,
                            artifactId);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError(
                "Node not found: "
                        + artifactId);
    }

    /**
     * Recursively searches children for
     * a node with the given artifactId.
     *
     * @param parent parent node
     * @param artifactId artifact id
     * @return matching node or null
     */
    private DependencyNode findInChildren(
            final DependencyNode parent,
            final String artifactId) {
        for (DependencyNode child
                : parent.getChildren()) {
            if (child.getArtifact()
                    .getArtifactId()
                    .equals(artifactId)) {
                return child;
            }
            final DependencyNode found =
                    findInChildren(child,
                            artifactId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
