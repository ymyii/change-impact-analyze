package io.github.dependencyanalysis.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link BuildResult}.
 */
class BuildResultTest {

    @Test
    void ofCreatesResult() {
        final ModuleBuildOutput mod =
                new ModuleBuildOutput(
                        Paths.get("/m"),
                        Paths.get("/c"));
        final BuildResult result =
                BuildResult.of(
                        Collections
                                .singletonList(
                                        mod));
        assertThat(result.getOutputs())
                .hasSize(1)
                .containsExactly(mod);
    }

    @Test
    void emptyList() {
        final BuildResult result =
                BuildResult.of(
                        Collections
                                .emptyList());
        assertThat(result.getOutputs())
                .isEmpty();
    }

    @Test
    void outputsAreUnmodifiable() {
        final BuildResult result =
                BuildResult.of(
                        Collections
                                .emptyList());
        assertThatThrownBy(
                () -> result.getOutputs()
                        .add(null))
                .isInstanceOf(
                        UnsupportedOperationException
                                .class);
    }

    @Test
    void nullListThrows() {
        assertThatThrownBy(
                () -> BuildResult.of(null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void toStringContainsOutputs() {
        final ModuleBuildOutput mod =
                new ModuleBuildOutput(
                        Paths.get("/m"),
                        Paths.get("/c"));
        final BuildResult result =
                BuildResult.of(
                        Collections
                                .singletonList(
                                        mod));
        assertThat(result.toString())
                .contains("BuildResult")
                .contains("outputs");
    }

    @Test
    void multipleOutputs() {
        final ModuleBuildOutput a =
                new ModuleBuildOutput(
                        Paths.get("/a"),
                        Paths.get("/a/c"));
        final ModuleBuildOutput b =
                new ModuleBuildOutput(
                        Paths.get("/b"),
                        Paths.get("/b/c"));
        final List<ModuleBuildOutput> list =
                Arrays.asList(a, b);
        final BuildResult result =
                BuildResult.of(list);
        assertThat(result.getOutputs())
                .hasSize(2)
                .containsExactly(a, b);
    }
}
