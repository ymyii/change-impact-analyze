package io.github.changeimpact.analyze.dependency;

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
