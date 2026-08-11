package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable dependency tree for a single
 * Maven module containing the module
 * coordinate and its dependencies.
 */
public final class ModuleDependencyTree {

    /** Module artifact coordinate. */
    private final ArtifactCoord module;

    /** Module directory path. */
    private final Path modulePath;

    /** Top-level dependencies. */
    private final List<DependencyNode>
            dependencies;

    /** Occurrence-preserving dependency graph. */
    private final ModuleDependencyOccurrenceGraph occurrenceGraph;

    /**
     * Creates a new module dependency
     * tree.
     *
     * @param mod module coordinate
     * @param path module directory path
     * @param deps dependency nodes
     */
    public ModuleDependencyTree(
            final ArtifactCoord mod,
            final Path path,
            final List<DependencyNode>
                    deps) {
        this(mod, path, deps, graphFromTree(mod, deps));
    }

    /**
     * Creates a tree plus its occurrence-preserving source graph.
     *
     * @param mod module coordinate
     * @param path module directory
     * @param deps coordinate-projection tree
     * @param graph occurrence graph
     */
    public ModuleDependencyTree(
            final ArtifactCoord mod,
            final Path path,
            final List<DependencyNode> deps,
            final ModuleDependencyOccurrenceGraph graph) {
        this.module =
                Objects.requireNonNull(
                        mod, "module");
        this.modulePath =
                Objects.requireNonNull(
                        path, "modulePath");
        this.dependencies =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                Objects
                                        .requireNonNull(
                                                deps,
                                                "dependencies")));
        this.occurrenceGraph = Objects.requireNonNull(graph,
                "occurrenceGraph");
    }

    /**
     * Returns the module coordinate.
     *
     * @return module artifact
     */
    public ArtifactCoord getModule() {
        return module;
    }

    /**
     * Returns the module directory path.
     *
     * @return module path
     */
    public Path getModulePath() {
        return modulePath;
    }

    /**
     * Returns unmodifiable list of top-
     * level dependency nodes.
     *
     * @return dependencies
     */
    public List<DependencyNode>
            getDependencies() {
        return dependencies;
    }

    /** @return structured occurrence graph used for path planning */
    public ModuleDependencyOccurrenceGraph getOccurrenceGraph() {
        return occurrenceGraph;
    }

    private static ModuleDependencyOccurrenceGraph graphFromTree(
            final ArtifactCoord module,
            final List<DependencyNode> roots) {
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                new ArrayList<>();
        final List<ModuleDependencyOccurrenceGraph.Edge> edges =
                new ArrayList<>();
        nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                "module", module, null, true, false));
        final int[] sequence = {0};
        for (DependencyNode root : roots) {
            append(root, "module", nodes, edges, sequence);
        }
        return new ModuleDependencyOccurrenceGraph("module", nodes, edges);
    }

    private static void append(
            final DependencyNode node,
            final String parent,
            final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes,
            final List<ModuleDependencyOccurrenceGraph.Edge> edges,
            final int[] sequence) {
        final String id = "tree-" + sequence[0]++;
        nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                id, node.getArtifact(), node.getScope(), false, false));
        edges.add(new ModuleDependencyOccurrenceGraph.Edge(parent, id));
        for (DependencyNode child : node.getChildren()) {
            append(child, id, nodes, edges, sequence);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o
                instanceof ModuleDependencyTree)) {
            return false;
        }
        final ModuleDependencyTree that =
                (ModuleDependencyTree) o;
        return module.equals(that.module)
                && modulePath.equals(
                        that.modulePath)
                && dependencies.equals(
                        that.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                module, modulePath,
                dependencies);
    }

    @Override
    public String toString() {
        return "ModuleDependencyTree{"
                + "module=" + module
                + ", modulePath="
                + modulePath
                + ", dependencies="
                + dependencies + '}';
    }
}
