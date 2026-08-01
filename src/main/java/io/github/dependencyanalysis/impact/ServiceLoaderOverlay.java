package io.github.dependencyanalysis.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable conservative ServiceLoader edge overlay. */
public final class ServiceLoaderOverlay {

    /** Empty overlay. */
    private static final ServiceLoaderOverlay EMPTY =
            new ServiceLoaderOverlay(List.of(), List.of());

    /** Overlay edges. */
    private final List<QueryEdge> edges;

    /** Coverage limitations. */
    private final List<String> limitations;

    /**
     * Creates an overlay.
     *
     * @param overlayEdges conservative edges
     * @param coverageLimitations unresolved resource or service evidence
     */
    public ServiceLoaderOverlay(
            final List<QueryEdge> overlayEdges,
            final List<String> coverageLimitations) {
        final List<QueryEdge> sorted = new ArrayList<>(overlayEdges);
        sorted.sort(Comparator
                .comparing((QueryEdge edge) ->
                        edge.getCaller().methodId().toString())
                .thenComparing(edge ->
                        edge.getCallee().methodId().toString())
                .thenComparing(QueryEdge::getEvidence));
        edges = Collections.unmodifiableList(sorted);
        limitations = List.copyOf(coverageLimitations);
    }

    /** @return singleton empty overlay */
    public static ServiceLoaderOverlay empty() {
        return EMPTY;
    }

    /** @return overlay edges */
    public List<QueryEdge> getEdges() {
        return edges;
    }

    /** @return coverage limitations */
    public List<String> getLimitations() {
        return limitations;
    }

    /** @return true when unresolved service evidence exists */
    public boolean isInconclusive() {
        return !limitations.isEmpty();
    }

    /**
     * Returns conservative predecessors for one node.
     *
     * @param node target node
     * @return predecessor nodes
     */
    public List<QueryNode> predecessors(final QueryNode node) {
        final Set<QueryNode> result = new LinkedHashSet<>();
        for (QueryEdge edge : edges) {
            if (edge.getCallee().equals(node)) {
                result.add(edge.getCaller());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Finds the materialized edge between two overlay nodes.
     *
     * @param caller predecessor
     * @param callee successor
     * @return matching edge, or null
     */
    public QueryEdge edge(final QueryNode caller, final QueryNode callee) {
        return edges.stream()
                .filter(value -> value.getCaller().equals(caller)
                        && value.getCallee().equals(callee))
                .findFirst().orElse(null);
    }

    /** @return unique overlay nodes */
    public List<QueryNode> nodes() {
        final Set<QueryNode> result = new LinkedHashSet<>();
        for (QueryEdge edge : edges) {
            result.add(edge.getCaller());
            result.add(edge.getCallee());
        }
        return List.copyOf(result);
    }
}
