package io.github.dependencyanalysis.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

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
                        1,
                        "COMPILATION ERROR",
                        Paths.get(
                                "/tmp/log"));
        final String msg = ex.getMessage();
        assertThat(msg).contains("baseline");
        assertThat(msg)
                .contains("/ws/mod-a");
        assertThat(msg)
                .contains("mvn compile -B");
        assertThat(msg).contains("1");
        assertThat(msg)
                .contains("COMPILATION");
        assertThat(msg)
                .contains("/tmp/log");
    }

    @Test
    void gettersReturnValues() {
        final BuildException ex =
                new BuildException(
                        "target",
                        "/ws",
                        "mvn compile -B",
                        2,
                        "error text",
                        Paths.get(
                                "/tmp/cia.log"));
        assertThat(ex.getSide())
                .isEqualTo("target");
        assertThat(ex.getModule())
                .isEqualTo("/ws");
        assertThat(ex.getCommand())
                .isEqualTo(
                        "mvn compile -B");
        assertThat(ex.getExitCode())
                .isEqualTo(2);
        assertThat(ex.getStderr())
                .isEqualTo("error text");
        assertThat(ex.getLogFile())
                .isEqualTo(Paths.get(
                        "/tmp/cia.log"));
    }

    @Test
    void isCheckedException() {
        final BuildException ex =
                new BuildException(
                        "s", "m", "c",
                        0, "", null);
        assertThat(ex)
                .isInstanceOf(Exception.class);
    }

    @Test
    void nullLogFileIsAllowed() {
        final BuildException ex =
                new BuildException(
                        "s", "m", "c",
                        1, "e", null);
        assertThat(ex.getLogFile())
                .isNull();
    }
}
