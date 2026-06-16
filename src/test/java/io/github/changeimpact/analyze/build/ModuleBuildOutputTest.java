package io.github.changeimpact.analyze.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link ModuleBuildOutput}.
 */
class ModuleBuildOutputTest {

    @Test
    void gettersReturnValues() {
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        Paths.get("/mod"),
                        Paths.get(
                                "/mod/t/c"));
        assertThat(out.getModulePath())
                .isEqualTo(
                        Paths.get("/mod"));
        assertThat(out.getClassesDir())
                .isEqualTo(
                        Paths.get(
                                "/mod/t/c"));
    }

    @Test
    void nullModuleThrows() {
        assertThatThrownBy(
                () -> new ModuleBuildOutput(
                        null,
                        Paths.get("/c")))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void nullClassesThrows() {
        assertThatThrownBy(
                () -> new ModuleBuildOutput(
                        Paths.get("/m"),
                        null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void toStringContainsFields() {
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        Paths.get("/mod"),
                        Paths.get(
                                "/mod/t/c"));
        final String s = out.toString();
        assertThat(s).contains("/mod");
        assertThat(s).contains("/mod/t/c");
    }

    @Test
    void equalsAndHashCode() {
        final ModuleBuildOutput a =
                new ModuleBuildOutput(
                        Paths.get("/m"),
                        Paths.get("/c"));
        final ModuleBuildOutput b =
                new ModuleBuildOutput(
                        Paths.get("/m"),
                        Paths.get("/c"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
    }

    @Test
    void notEqualDifferentPath() {
        final ModuleBuildOutput a =
                new ModuleBuildOutput(
                        Paths.get("/m1"),
                        Paths.get("/c"));
        final ModuleBuildOutput b =
                new ModuleBuildOutput(
                        Paths.get("/m2"),
                        Paths.get("/c"));
        assertThat(a).isNotEqualTo(b);
    }
}
