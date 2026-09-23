package io.github.dependencyanalysis.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link BuildException}.
 */
class BuildExceptionTest {

    @Test
    void messageContainsAllFields() {
        final BuildException ex =
                new BuildException(
                        "baseline",
                        "/ws/mod-a",
                        "mvn compile -B",
                        1);
        final String msg = ex.getMessage();
        assertThat(msg).contains("baseline");
        assertThat(msg)
                .contains("/ws/mod-a");
        assertThat(msg)
                .contains("mvn compile -B");
        assertThat(msg).contains("1");
        assertThat(msg).doesNotContain("stderr");
        assertThat(msg)
                .doesNotContain("logFile");
    }

    @Test
    void gettersReturnValues() {
        final BuildException ex =
                new BuildException(
                        "target",
                        "/ws",
                        "mvn compile -B",
                        2);
        assertThat(ex.getSide())
                .isEqualTo("target");
        assertThat(ex.getModule())
                .isEqualTo("/ws");
        assertThat(ex.getCommand())
                .isEqualTo(
                        "mvn compile -B");
        assertThat(ex.getExitCode())
                .isEqualTo(2);
    }

    @Test
    void isCheckedException() {
        final BuildException ex =
                new BuildException(
                        "s", "m", "c",
                        0);
        assertThat(ex)
                .isInstanceOf(Exception.class);
    }

}
