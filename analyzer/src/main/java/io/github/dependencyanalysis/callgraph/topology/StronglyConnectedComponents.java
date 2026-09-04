package io.github.dependencyanalysis.callgraph.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Deterministic iterative strongly connected component decomposition. */
public final class StronglyConnectedComponents {

    private StronglyConnectedComponents() {
    }

    /**
     * Decomposes one directed graph using caller-provided adjacency.
     *
     * @param nodes complete node collection
     * @param successors outgoing adjacency
     * @param predecessors incoming adjacency
     * @param order stable node order
     * @param <N> node type
     * @return stable components with stable nodes
     */
    public static <N> List<List<N>> decompose(
            final Collection<N> nodes,
            final Function<N, ? extends Collection<N>> successors,
            final Function<N, ? extends Collection<N>> predecessors,
            final Comparator<N> order) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(successors, "successors");
        Objects.requireNonNull(predecessors, "predecessors");
        Objects.requireNonNull(order, "order");
        final List<N> stableNodes = nodes.stream().sorted(order).toList();
        final List<N> finishing = finishingOrder(
                stableNodes, successors, order);
        final Set<N> assigned = new HashSet<>();
        final List<List<N>> result = new ArrayList<>();
        for (int index = finishing.size() - 1; index >= 0; index--) {
            final N root = finishing.get(index);
            if (!assigned.add(root)) {
                continue;
            }
            final List<N> component = new ArrayList<>();
            final Deque<N> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                final N current = stack.pop();
                component.add(current);
                final List<N> incoming = predecessors.apply(current).stream()
                        .sorted(order.reversed()).toList();
                for (N predecessor : incoming) {
                    if (assigned.add(predecessor)) {
                        stack.push(predecessor);
                    }
                }
            }
            component.sort(order);
            result.add(List.copyOf(component));
        }
        result.sort((left, right) -> order.compare(left.get(0), right.get(0)));
        return List.copyOf(result);
    }

    private static <N> List<N> finishingOrder(
            final List<N> nodes,
            final Function<N, ? extends Collection<N>> successors,
            final Comparator<N> order) {
        final List<N> result = new ArrayList<>();
        final Set<N> visited = new HashSet<>();
        for (N node : nodes) {
            if (!visited.add(node)) {
                continue;
            }
            final Deque<TraversalFrame<N>> stack = new ArrayDeque<>();
            stack.push(frame(node, successors, order));
            while (!stack.isEmpty()) {
                final TraversalFrame<N> current = stack.peek();
                if (current.successors().hasNext()) {
                    final N successor = current.successors().next();
                    if (visited.add(successor)) {
                        stack.push(frame(successor, successors, order));
                    }
                } else {
                    result.add(current.node());
                    stack.pop();
                }
            }
        }
        return result;
    }

    private static <N> TraversalFrame<N> frame(
            final N node,
            final Function<N, ? extends Collection<N>> successors,
            final Comparator<N> order) {
        return new TraversalFrame<>(node, successors.apply(node).stream()
                .sorted(order).iterator());
    }

    /**
     * Iterative depth-first traversal frame.
     *
     * @param <N> node type
     * @param node current node
     * @param successors ordered successor iterator
     */
    private record TraversalFrame<N>(N node, Iterator<N> successors) {
    }
}
