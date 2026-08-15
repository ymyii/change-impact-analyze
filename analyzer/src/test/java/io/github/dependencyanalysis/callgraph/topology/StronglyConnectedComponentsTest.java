package io.github.dependencyanalysis.callgraph.topology;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests canonical deterministic SCC decomposition. */
class StronglyConnectedComponentsTest {

    @Test
    void decomposesSelfRecursiveMutualAndAcyclicComponents() {
        final Map<String, Set<String>> outgoing = Map.of(
                "a", Set.of("a", "b"),
                "b", Set.of("c"),
                "c", Set.of("b"),
                "d", Set.of("c"));
        final Map<String, Set<String>> incoming = Map.of(
                "a", Set.of("a"),
                "b", Set.of("a", "c"),
                "c", Set.of("b", "d"),
                "d", Set.of());

        final List<List<String>> components =
                StronglyConnectedComponents.decompose(
                        outgoing.keySet(),
                        node -> outgoing.getOrDefault(node, Set.of()),
                        node -> incoming.getOrDefault(node, Set.of()),
                        Comparator.naturalOrder());

        assertThat(components).containsExactly(
                List.of("a"), List.of("b", "c"), List.of("d"));
    }

    @Test
    void resultIsIndependentOfInputIterationOrder() {
        final Map<String, Set<String>> adjacency = Map.of(
                "a", Set.of("b"), "b", Set.of("a"),
                "c", Set.of("d"), "d", Set.of());
        final Map<String, Set<String>> reverse = Map.of(
                "a", Set.of("b"), "b", Set.of("a"),
                "c", Set.of(), "d", Set.of("c"));

        final List<List<String>> forward = decompose(
                List.of("a", "b", "c", "d"), adjacency, reverse);
        final List<List<String>> reversed = decompose(
                List.of("d", "c", "b", "a"), adjacency, reverse);

        assertThat(reversed).isEqualTo(forward);
    }

    private List<List<String>> decompose(
            final List<String> nodes,
            final Map<String, Set<String>> adjacency,
            final Map<String, Set<String>> reverse) {
        return StronglyConnectedComponents.decompose(nodes,
                node -> adjacency.getOrDefault(node, Set.of()),
                node -> reverse.getOrDefault(node, Set.of()),
                Comparator.naturalOrder());
    }
}
