package io.github.dependencyanalysis.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable GraphML occurrence graph retaining every parent and child edge. */
public final class ModuleDependencyOccurrenceGraph {

    /**
     * One GraphML dependency occurrence.
     *
     * @param id occurrence identity
     * @param artifact Maven artifact
     * @param scope retained dependency scope, null only for Module root
     * @param moduleRoot whether this is the Module root
     * @param reactor whether this is a reactor dependency
     */
    public record Occurrence(
            String id,
            ArtifactCoord artifact,
            DependencyScope scope,
            boolean moduleRoot,
            boolean reactor) {

        /** Validates occurrence identity. */
        public Occurrence {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(artifact, "artifact");
            if (!moduleRoot) {
                Objects.requireNonNull(scope, "scope");
            }
        }
    }

    /**
     * Directed parent-to-child GraphML edge.
     *
     * @param parentId parent occurrence identity
     * @param childId child occurrence identity
     */
    public record Edge(String parentId, String childId) {

        /** Validates edge endpoints. */
        public Edge {
            Objects.requireNonNull(parentId, "parentId");
            Objects.requireNonNull(childId, "childId");
        }
    }

    /**
     * Stable validation result used by changed-path planning.
     *
     * @param valid whether graph validation succeeded
     * @param reason stable failure reason, empty on success
     */
    public record Validation(boolean valid, String reason) {

        /** Validates result. */
        public Validation {
            Objects.requireNonNull(reason, "reason");
        }

        /** @return successful validation */
        public static Validation success() {
            return new Validation(true, "");
        }

        /**
         * @param reason failure reason
         * @return failed validation
         */
        public static Validation failure(final String reason) {
            return new Validation(false, reason);
        }
    }

    /** Module root occurrence id. */
    private final String rootId;

    /** Occurrences by GraphML identity. */
    private final Map<String, Occurrence> occurrences;

    /** Stable edge list. */
    private final List<Edge> edges;

    /** Parent occurrence ids by child id. */
    private final Map<String, List<String>> parents;

    /** Child occurrence ids by parent id. */
    private final Map<String, List<String>> children;

    /**
     * Creates an occurrence graph.
     *
     * @param moduleRootId module root identity
     * @param nodes GraphML occurrences
     * @param graphEdges parent-to-child edges
     */
    public ModuleDependencyOccurrenceGraph(
            final String moduleRootId,
            final List<Occurrence> nodes,
            final List<Edge> graphEdges) {
        rootId = Objects.requireNonNull(moduleRootId, "moduleRootId");
        final Map<String, Occurrence> indexed = new LinkedHashMap<>();
        for (Occurrence node : Objects.requireNonNull(nodes, "nodes")) {
            if (indexed.put(node.id(), node) != null) {
                throw new IllegalArgumentException(
                        "Duplicate dependency occurrence id: " + node.id());
            }
        }
        occurrences = Collections.unmodifiableMap(indexed);
        edges = List.copyOf(Objects.requireNonNull(graphEdges, "edges"));
        parents = adjacency(edges, false);
        children = adjacency(edges, true);
    }

    private static Map<String, List<String>> adjacency(
            final List<Edge> values, final boolean forward) {
        final Map<String, List<String>> mutable = new LinkedHashMap<>();
        for (Edge edge : values) {
            final String key = forward ? edge.parentId() : edge.childId();
            final String value = forward ? edge.childId() : edge.parentId();
            mutable.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(value);
        }
        final Map<String, List<String>> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    /** @return module root occurrence */
    public Occurrence root() {
        return occurrences.get(rootId);
    }

    /** @return stable occurrence list */
    public List<Occurrence> occurrences() {
        return List.copyOf(occurrences.values());
    }

    /** @return stable edge list */
    public List<Edge> edges() {
        return edges;
    }

    /**
     * @param id occurrence id
     * @return occurrence or null
     */
    public Occurrence occurrence(final String id) {
        return occurrences.get(id);
    }

    /**
     * @param id child id
     * @return all parent occurrences
     */
    public List<Occurrence> parentsOf(final String id) {
        return nodes(parents.getOrDefault(id, List.of()));
    }

    /**
     * @param id parent id
     * @return all child occurrences
     */
    public List<Occurrence> childrenOf(final String id) {
        return nodes(children.getOrDefault(id, List.of()));
    }

    /**
     * @param artifact coordinate
     * @return every matching occurrence
     */
    public List<Occurrence> occurrencesOf(final ArtifactCoord artifact) {
        return occurrences.values().stream()
                .filter(value -> value.artifact().equals(artifact))
                .toList();
    }

    private List<Occurrence> nodes(final List<String> ids) {
        return ids.stream().map(occurrences::get)
                .filter(Objects::nonNull).toList();
    }

    /**
     * Validates the single-root, complete-edge, reachable, acyclic contract.
     *
     * @return validation result
     */
    public Validation validate() {
        final Occurrence root = occurrences.get(rootId);
        if (root == null || !root.moduleRoot()) {
            return Validation.failure("MODULE_ROOT_MISSING");
        }
        final long roots = occurrences.values().stream()
                .filter(Occurrence::moduleRoot).count();
        if (roots != 1L) {
            return Validation.failure("MULTIPLE_MODULE_ROOTS");
        }
        for (Edge edge : edges) {
            if (!occurrences.containsKey(edge.parentId())
                    || !occurrences.containsKey(edge.childId())) {
                return Validation.failure("PARENT_EDGE_INCOMPLETE");
            }
        }
        if (hasCycle(rootId, new LinkedHashSet<>(),
                new LinkedHashSet<>())) {
            return Validation.failure("DEPENDENCY_GRAPH_CYCLE");
        }
        final Set<String> reachable = new LinkedHashSet<>();
        final Deque<String> work = new ArrayDeque<>();
        work.add(rootId);
        while (!work.isEmpty()) {
            final String current = work.removeFirst();
            if (reachable.add(current)) {
                work.addAll(children.getOrDefault(current, List.of()));
            }
        }
        if (reachable.size() != occurrences.size()) {
            return Validation.failure("UNREACHABLE_DEPENDENCY_OCCURRENCE");
        }
        return Validation.success();
    }

    private boolean hasCycle(
            final String current,
            final Set<String> visited,
            final Set<String> active) {
        if (active.contains(current)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        active.add(current);
        for (String child : children.getOrDefault(current, List.of())) {
            if (hasCycle(child, visited, active)) {
                return true;
            }
        }
        active.remove(current);
        return false;
    }
}
