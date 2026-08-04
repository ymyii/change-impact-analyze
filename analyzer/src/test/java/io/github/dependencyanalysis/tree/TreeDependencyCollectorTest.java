package io.github.dependencyanalysis.tree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tree dependency collector diagnostics tests. */
class TreeDependencyCollectorTest {

    /** Total fixture lines. */
    private static final int TOTAL_LINES = 105;

    /** First retained line. */
    private static final int FIRST_RETAINED_LINE = 6;

    /** Expected diagnostic line count. */
    private static final int EXPECTED_LINES = 100;

    @Test
    void diagnosticTailKeepsExactlyLastOneHundredLines() {
        final String output = IntStream.rangeClosed(
                        1, TOTAL_LINES)
                .mapToObj(value -> String.format(
                        "%03d", value))
                .collect(Collectors.joining("\n")) + "\n\n";

        final String tail = TreeDependencyCollector
                .diagnosticTail(output);

        assertThat(tail.lines()).hasSize(EXPECTED_LINES);
        assertThat(tail).startsWith(String.format(
                        "%03d", FIRST_RETAINED_LINE))
                .endsWith("105")
                .doesNotContain("005");
    }

    @Test
    void excludesOnlyMultiProjectPomPackagingRoot() {
        final Path root = Path.of("pom.xml");
        final Path child = Path.of("child/pom.xml");
        final List<Path> active = List.of(root, child);
        final ReactorDescriptor reactor = new ReactorDescriptor(
                root, "test:root:1", active, active,
                List.of());
        final RepositoryInventory inventory = inventory(
                reactor, "pom", List.of("child"));

        assertThat(reactor.getActivePoms())
                .containsExactlyElementsOf(active);
        assertThat(reactor.isRootSelected()).isTrue();
        assertThat(inventory.analysisPoms(
                reactor, reactor.getActivePoms()))
                .containsExactly(child);
        assertThat(inventory.analysisPoms(
                reactor, List.of(root))).isEmpty();
    }

    @Test
    void keepsSinglePomAndNonPomRootsAsModules() {
        final Path root = Path.of("pom.xml");
        final ReactorDescriptor single = new ReactorDescriptor(
                root, "test:single:1", List.of(root),
                List.of(root), List.of());
        final RepositoryInventory singleInventory = inventory(
                single, "pom", List.of("missing-child"));

        assertThat(singleInventory.analysisPoms(
                single, single.getActivePoms()))
                .containsExactly(root);

        for (String packaging : List.of("jar", "war")) {
            final Path child = Path.of(
                    packaging, "pom.xml");
            final List<Path> active = List.of(root, child);
            final ReactorDescriptor reactor =
                    new ReactorDescriptor(root,
                            "test:" + packaging + ":1",
                            active, active, List.of());
            final RepositoryInventory inventory = inventory(
                    reactor, packaging,
                    List.of(packaging));

            assertThat(inventory.analysisPoms(
                    reactor, reactor.getActivePoms()))
                    .containsExactlyElementsOf(active);
        }
    }

    private RepositoryInventory inventory(
            final ReactorDescriptor reactor,
            final String rootPackaging,
            final List<String> declaredModules) {
        final Map<Path, PomDescriptor> poms =
                new LinkedHashMap<>();
        for (Path pom : reactor.getActivePoms()) {
            final boolean root = pom.equals(
                    reactor.getRootPom());
            poms.put(pom, new PomDescriptor(
                    pom, root ? reactor.getCoordinate()
                    : "test:child:1",
                    root ? rootPackaging : "jar",
                    root ? declaredModules : List.of(),
                    root ? declaredModules : List.of(), ""));
        }
        return new RepositoryInventory(
                List.of(reactor), poms);
    }
}
