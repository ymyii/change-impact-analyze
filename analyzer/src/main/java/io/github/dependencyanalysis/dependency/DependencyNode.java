package io.github.dependencyanalysis.dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable node in a dependency tree
 * representing a single artifact with
 * its scope and child dependencies.
 */
public final class DependencyNode {

    /** Artifact coordinate. */
    private final ArtifactCoord artifact;

    /** Dependency scope. */
    private final DependencyScope scope;

    /** Child dependency nodes. */
    private final List<DependencyNode>
            children;

    /**
     * Creates a new dependency node.
     *
     * @param art artifact coordinate
     * @param scp dependency scope
     * @param kids child nodes
     */
    public DependencyNode(
            final ArtifactCoord art,
            final DependencyScope scp,
            final List<DependencyNode>
                    kids) {
        this.artifact =
                Objects.requireNonNull(
                        art, "artifact");
        this.scope =
                Objects.requireNonNull(
                        scp, "scope");
        this.children =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                Objects
                                        .requireNonNull(
                                                kids,
                                                "children")));
    }

    /**
     * Returns the artifact coordinate.
     *
     * @return artifact
     */
    public ArtifactCoord getArtifact() {
        return artifact;
    }

    /**
     * Returns the dependency scope.
     *
     * @return scope
     */
    public DependencyScope getScope() {
        return scope;
    }

    /**
     * Returns unmodifiable list of child
     * dependency nodes.
     *
     * @return children
     */
    public List<DependencyNode>
            getChildren() {
        return children;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyNode)) {
            return false;
        }
        final DependencyNode that =
                (DependencyNode) o;
        return artifact.equals(
                that.artifact)
                && scope == that.scope
                && children.equals(
                        that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                artifact, scope,
                children);
    }

    @Override
    public String toString() {
        return "DependencyNode{"
                + "artifact=" + artifact
                + ", scope=" + scope
                + ", children=" + children
                + '}';
    }
}
