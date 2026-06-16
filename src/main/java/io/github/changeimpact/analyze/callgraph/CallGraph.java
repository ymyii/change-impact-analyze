package io.github.changeimpact.analyze.callgraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable call graph container
 * holding methods, call edges, and
 * override relationships.
 */
public final class CallGraph {

    /** All indexed method ids. */
    private final Set<MethodId> methods;

    /** All call edges. */
    private final List<CallEdge> edges;

    /** Edges indexed by caller. */
    private final Map<MethodId,
            List<CallEdge>> outgoing;

    /** Edges indexed by callee. */
    private final Map<MethodId,
            List<CallEdge>> incoming;

    /** Override map: parent method
     *  to overriding method ids. */
    private final Map<MethodId,
            Set<MethodId>> overrides;

    /** Construction statistics. */
    private final CallGraphStats stats;

    /**
     * Package-private constructor
     * used by CallGraphEngine.
     *
     * @param mts   all method ids
     * @param edgs  all call edges
     * @param ovrds override map
     * @param st    statistics
     */
    CallGraph(
            final Set<MethodId> mts,
            final List<CallEdge> edgs,
            final Map<MethodId,
                    Set<MethodId>> ovrds,
            final CallGraphStats st) {
        this.methods =
                Collections
                        .unmodifiableSet(
                                new HashSet<>(mts));
        this.edges =
                Collections
                        .unmodifiableList(
                                new ArrayList<>(edgs));
        this.overrides =
                Collections
                        .unmodifiableMap(
                                new HashMap<>(ovrds));
        this.stats = st;
        this.outgoing = new HashMap<>();
        this.incoming = new HashMap<>();
        for (final CallEdge e : edgs) {
            outgoing.computeIfAbsent(
                            e.getCaller(),
                            k -> new ArrayList<>())
                    .add(e);
            incoming.computeIfAbsent(
                            e.getCallee(),
                            k -> new ArrayList<>())
                    .add(e);
        }
    }

    /**
     * Returns all indexed method ids.
     *
     * @return unmodifiable set of
     *  method ids
     */
    public Set<MethodId> getMethods() {
        return methods;
    }

    /**
     * Returns all call edges.
     *
     * @return unmodifiable list of
     *  call edges
     */
    public List<CallEdge> getEdges() {
        return edges;
    }

    /**
     * Returns outgoing edges from a
     * caller method.
     *
     * @param method caller method id
     * @return list of outgoing edges
     */
    public List<CallEdge>
            getOutgoingEdges(
            final MethodId method) {
        final List<CallEdge> found =
                outgoing.get(method);
        if (found == null) {
            return Collections
                    .emptyList();
        }
        return Collections
                .unmodifiableList(found);
    }

    /**
     * Returns incoming edges to a
     * callee method.
     *
     * @param method callee method id
     * @return list of incoming edges
     */
    public List<CallEdge>
            getIncomingEdges(
            final MethodId method) {
        final List<CallEdge> found =
                incoming.get(method);
        if (found == null) {
            return Collections
                    .emptyList();
        }
        return Collections
                .unmodifiableList(found);
    }

    /**
     * Returns methods that override
     * the given method.
     *
     * @param method parent method id
     * @return set of overriding ids
     */
    public Set<MethodId> getOverrides(
            final MethodId method) {
        final Set<MethodId> found =
                overrides.get(method);
        if (found == null) {
            return Collections
                    .emptySet();
        }
        return Collections
                .unmodifiableSet(found);
    }

    /**
     * Returns construction statistics.
     *
     * @return call graph statistics
     */
    public CallGraphStats getStats() {
        return stats;
    }

    @Override
    public String toString() {
        return "CallGraph{"
                + "methods="
                + methods.size()
                + ", edges="
                + edges.size()
                + '}';
    }
}
