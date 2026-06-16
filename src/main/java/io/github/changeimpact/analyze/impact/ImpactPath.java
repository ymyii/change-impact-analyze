package io.github.changeimpact.analyze.impact;

import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.callgraph.CallEdge;
import io.github.changeimpact.analyze.callgraph.MethodId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable single impact path
 * from an affected root method
 * to a change point via call
 * edges.
 */
public final class ImpactPath {

    /** Affected root method. */
    private final MethodId
            affectedMethod;

    /** The change point. */
    private final ChangePoint
            changePoint;

    /** Call edges from root to
     *  seed method. */
    private final List<CallEdge>
            edges;

    /** Modules traversed. */
    private final Set<String>
            modules;

    /** Cross-module boundary
     *  descriptions. */
    private final List<String>
            crossModuleBoundaries;

    /**
     * Creates a new impact path.
     *
     * @param affected affected root
     * @param cp       change point
     * @param edgs     call edges
     * @param mods     modules traversed
     * @param bounds   cross-module
     *                 boundary descriptions
     */
    public ImpactPath(
            final MethodId affected,
            final ChangePoint cp,
            final List<CallEdge> edgs,
            final Set<String> mods,
            final List<String> bounds) {
        this.affectedMethod =
                Objects.requireNonNull(
                        affected,
                        "affectedMethod");
        this.changePoint =
                Objects.requireNonNull(
                        cp, "changePoint");
        this.edges = Collections
                .unmodifiableList(
                        new ArrayList<>(edgs));
        this.modules = Collections
                .unmodifiableSet(
                        new LinkedHashSet<>(
                                mods));
        this.crossModuleBoundaries =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                bounds));
    }

    /**
     * Returns the affected root
     * method.
     *
     * @return affected method id
     */
    public MethodId
            getAffectedMethod() {
        return affectedMethod;
    }

    /**
     * Returns the change point.
     *
     * @return change point
     */
    public ChangePoint
            getChangePoint() {
        return changePoint;
    }

    /**
     * Returns the call edges
     * from root to seed.
     *
     * @return unmodifiable edge
     *  list
     */
    public List<CallEdge>
            getEdges() {
        return edges;
    }

    /**
     * Returns the modules
     * traversed.
     *
     * @return unmodifiable module
     *  set
     */
    public Set<String>
            getModules() {
        return modules;
    }

    /**
     * Returns cross-module
     * boundary descriptions.
     *
     * @return unmodifiable list
     *  of boundary strings
     */
    public List<String>
            getCrossModuleBoundaries() {
        return crossModuleBoundaries;
    }

    @Override
    public boolean equals(
            final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImpactPath)) {
            return false;
        }
        final ImpactPath that =
                (ImpactPath) o;
        return affectedMethod.equals(
                        that.affectedMethod)
                && changePoint.equals(
                        that.changePoint)
                && edges.equals(
                        that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                affectedMethod,
                changePoint, edges);
    }

    @Override
    public String toString() {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("ImpactPath{")
                .append("affected=")
                .append(affectedMethod)
                .append(", cp=")
                .append(changePoint)
                .append(", edges=")
                .append(edges.size())
                .append(", modules=")
                .append(modules)
                .append(", boundaries=")
                .append(
                        crossModuleBoundaries)
                .append('}');
        return sb.toString();
    }
}
