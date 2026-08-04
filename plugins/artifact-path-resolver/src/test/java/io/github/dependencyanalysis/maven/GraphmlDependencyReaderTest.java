package io.github.dependencyanalysis.maven;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests secure, scope-authoritative GraphML reading. */
class GraphmlDependencyReaderTest {

    @Test
    void readsSelectedScopesAndDropsExcludedSubtrees() throws Exception {
        final GraphmlDependencyReader.Graph graph =
                GraphmlDependencyReader.read(resource("selected.graphml"));

        assertThat(graph.getModule().identity())
                .isEqualTo("fixture:module:jar::1");
        final List<GraphmlDependencyReader.Dependency> dependencies =
                graph.getDependencies();
        assertThat(dependencies)
                .extracting(value -> value.getCoordinate().identity())
                .containsExactly(
                        "fixture:compile-lib:jar::1",
                        "fixture:native-lib:jar:linux-x86_64:1",
                        "fixture:runtime-lib:zip::1",
                        "fixture:system-lib:jar::1");
        assertThat(dependencies)
                .extracting(GraphmlDependencyReader.Dependency::getScope)
                .containsExactly(
                        "compile", "provided", "runtime", "system");
    }

    @Test
    void rejectsDuplicateBinding() throws Exception {
        assertThatThrownBy(() -> GraphmlDependencyReader.read(
                resource("duplicate.graphml")))
                .isInstanceOf(
                        GraphmlDependencyReader.GraphmlReadException.class)
                .hasMessageContaining("Duplicate GraphML dependency binding");
    }

    @Test
    void rejectsConflictingScopes() throws Exception {
        assertThatThrownBy(() -> GraphmlDependencyReader.read(
                resource("scope-conflict.graphml")))
                .isInstanceOf(
                        GraphmlDependencyReader.GraphmlReadException.class)
                .hasMessageContaining("Conflicting GraphML dependency scopes");
    }

    @Test
    void rejectsDoctypeAndExternalEntity() throws Exception {
        assertThatThrownBy(() -> GraphmlDependencyReader.read(
                resource("xxe.graphml")))
                .isInstanceOf(
                        GraphmlDependencyReader.GraphmlReadException.class)
                .hasMessageContaining("Unable to parse dependency GraphML");
    }

    private Path resource(final String filename)
            throws URISyntaxException {
        return Paths.get(getClass().getResource(
                "/graphml/" + filename).toURI());
    }
}
