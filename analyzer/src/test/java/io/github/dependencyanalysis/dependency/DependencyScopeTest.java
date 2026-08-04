package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link DependencyScope}.
 */
class DependencyScopeTest {

    @Test
    void fromStringCompile() {
        assertThat(DependencyScope
                .fromString("compile"))
                .isEqualTo(
                        DependencyScope
                                .COMPILE);
    }

    @Test
    void fromStringRuntime() {
        assertThat(DependencyScope
                .fromString("runtime"))
                .isEqualTo(
                        DependencyScope
                                .RUNTIME);
    }

    @Test
    void fromStringProvided() {
        assertThat(DependencyScope
                .fromString("provided"))
                .isEqualTo(
                        DependencyScope
                                .PROVIDED);
    }

    @Test
    void fromStringTestReturnsNull() {
        assertThat(DependencyScope
                .fromString("test"))
                .isNull();
    }

    @Test
    void fromStringUnknownReturnsNull() {
        assertThat(DependencyScope
                .fromString("system"))
                .isNull();
    }

    @Test
    void fromStringNullReturnsNull() {
        assertThat(DependencyScope
                .fromString(null))
                .isNull();
    }

    @Test
    void fromStringCaseInsensitive() {
        assertThat(DependencyScope
                .fromString("COMPILE"))
                .isEqualTo(
                        DependencyScope
                                .COMPILE);
        assertThat(DependencyScope
                .fromString("Runtime"))
                .isEqualTo(
                        DependencyScope
                                .RUNTIME);
    }

    @Test
    void fromStringTrimsWhitespace() {
        assertThat(DependencyScope
                .fromString("  compile  "))
                .isEqualTo(
                        DependencyScope
                                .COMPILE);
    }

    @Test
    void getValueReturnsString() {
        assertThat(DependencyScope.COMPILE
                .getValue())
                .isEqualTo("compile");
        assertThat(DependencyScope.RUNTIME
                .getValue())
                .isEqualTo("runtime");
        assertThat(DependencyScope.PROVIDED
                .getValue())
                .isEqualTo("provided");
    }
}
