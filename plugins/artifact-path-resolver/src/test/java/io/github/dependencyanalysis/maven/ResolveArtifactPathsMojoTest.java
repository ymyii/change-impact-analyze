package io.github.dependencyanalysis.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Physical artifact binding merge tests. */
class ResolveArtifactPathsMojoTest {

    /** Temporary artifact directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void mergesEqualPathsAndRejectsPhysicalAmbiguity() throws Exception {
        final ResolveArtifactPathsMojo mojo =
                new ResolveArtifactPathsMojo();
        final ArtifactCoordinates coordinates = new ArtifactCoordinates(
                "g", "a", "jar", "jar", "", "1", "1");
        final Path first = Files.createFile(
                temporaryDirectory.resolve("first.jar")).toRealPath();
        final Path second = Files.createFile(
                temporaryDirectory.resolve("second.jar")).toRealPath();
        final Map<String, ResolvedArtifactPath> bindings =
                new LinkedHashMap<String, ResolvedArtifactPath>();

        mojo.addResolvedBinding(bindings,
                new ResolvedArtifactPath(coordinates, first));
        mojo.addResolvedBinding(bindings,
                new ResolvedArtifactPath(coordinates, first));

        assertThat(bindings).hasSize(1);
        assertThatThrownBy(() -> mojo.addResolvedBinding(bindings,
                new ResolvedArtifactPath(coordinates, second)))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("multiple physical paths")
                .hasMessageContaining(first.toString())
                .hasMessageContaining(second.toString());
    }
}
