package io.github.dependencyanalysis.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GitCommandResult}.
 */
class GitCommandResultTest {

    @Test
    void staticFactorySetsFields() {
        final GitCommandResult r =
                GitCommandResult.of(0, "out");
        assertThat(r.getExitCode()).isZero();
        assertThat(r.getStdout())
                .isEqualTo("out");
    }

    /** Typical git fatal exit code. */
    private static final int GIT_FATAL =
            128;

    @Test
    void nonZeroExitCode() {
        final GitCommandResult r =
                GitCommandResult.of(
                        GIT_FATAL, "");
        assertThat(r.getExitCode())
                .isEqualTo(GIT_FATAL);
    }

    @Test
    void toStringContainsFields() {
        final GitCommandResult r =
                GitCommandResult.of(1, "hello");
        final String s = r.toString();
        assertThat(s).contains("1");
        assertThat(s).contains("hello");
    }
}
