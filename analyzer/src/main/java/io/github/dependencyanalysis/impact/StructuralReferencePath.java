package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.model.MethodId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Representative path from a PROJECT boundary to a metadata reference. */
public final class StructuralReferencePath {

    /** Changed dependency class. */
    private final BoundChangePoint changePoint;

    /** Terminal metadata relation. */
    private final StructuralReference reference;

    /** Ordered call nodes, empty for a direct PROJECT class-level reference. */
    private final List<QueryNode> nodes;

    /** Direct or transitive classification. */
    private final ImpactClassification classification;

    /**
     * Creates a Structural Reference Path.
     *
     * @param point changed class
     * @param structuralReference terminal metadata reference
     * @param orderedNodes PROJECT-to-reference call nodes
     * @param value direct or transitive classification
     */
    public StructuralReferencePath(
            final BoundChangePoint point,
            final StructuralReference structuralReference,
            final List<QueryNode> orderedNodes,
            final ImpactClassification value) {
        changePoint = Objects.requireNonNull(point, "point");
        reference = Objects.requireNonNull(
                structuralReference, "structuralReference");
        nodes = Collections.unmodifiableList(
                new ArrayList<>(orderedNodes));
        classification = Objects.requireNonNull(value, "classification");
    }

    /** @return changed dependency class */
    public BoundChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return terminal metadata relation */
    public StructuralReference getReference() {
        return reference;
    }

    /** @return ordered call nodes, possibly empty */
    public List<QueryNode> getNodes() {
        return nodes;
    }

    /** @return direct or transitive classification */
    public ImpactClassification getClassification() {
        return classification;
    }

    /**
     * @return affected PROJECT method, or null for a class-level direct path
     */
    public MethodId getAffectedMethod() {
        return nodes.isEmpty() ? null : nodes.get(0).methodId();
    }

    /** @return true for a PROJECT metadata reference without a method root */
    public boolean isDirectClassReference() {
        return nodes.isEmpty();
    }
}
