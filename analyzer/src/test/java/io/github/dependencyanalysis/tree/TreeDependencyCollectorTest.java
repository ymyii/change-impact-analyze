package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.PomDescriptor;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorScopeMode;
import io.github.dependencyanalysis.reactor.RepositoryInventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tree dependency collector diagnostics tests. */
class TreeDependencyCollectorTest {

    @Test
    void excludesPurePomAggregatorsFromAnalysisModules() {
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
        assertThat(reactor.getScopeMode())
                .isEqualTo(ReactorScopeMode.FULL_REACTOR);
        assertThat(inventory.analysisPoms(
                reactor, reactor.getActivePoms()))
                .containsExactly(child);
        assertThat(inventory.analysisPoms(
                reactor, List.of(root))).isEmpty();

        final Path nested = Path.of("nested/pom.xml");
        final Path leaf = Path.of("nested/leaf/pom.xml");
        final List<Path> nestedActive = List.of(root, nested, leaf);
        final ReactorDescriptor nestedReactor = new ReactorDescriptor(
                root, "test:root:1", nestedActive, nestedActive,
                List.of());
        final Map<Path, PomDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put(root, new PomDescriptor(root, "test:root:1",
                "pom", List.of("nested"), List.of("nested"), ""));
        descriptors.put(nested, new PomDescriptor(nested, "test:nested:1",
                "pom", List.of("leaf"), List.of("leaf"), ""));
        descriptors.put(leaf, new PomDescriptor(leaf, "test:leaf:1",
                "jar", List.of(), List.of(), ""));
        final RepositoryInventory nestedInventory = new RepositoryInventory(
                List.of(nestedReactor), descriptors);

        assertThat(nestedInventory.analysisPoms(
                nestedReactor, nestedActive)).containsExactly(leaf);
    }

    @Test
    void keepsSinglePomAndNonPomRootsAsModules() {
        final Path root = Path.of("pom.xml");
        final ReactorDescriptor single = new ReactorDescriptor(
                root, "test:single:1", List.of(root),
                List.of(root), List.of());
        final RepositoryInventory singleInventory = inventory(
                single, "pom", List.of());

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
