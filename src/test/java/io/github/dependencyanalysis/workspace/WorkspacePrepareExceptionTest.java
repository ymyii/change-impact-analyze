package io.github.dependencyanalysis.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkspacePrepareException}.
 */
class WorkspacePrepareExceptionTest {

    @Test
    void messageContainsAllFields() {
        final WorkspacePrepareException ex =
                new WorkspacePrepareException(
                        WorkspaceSide.BASELINE,
                        "abc123",
                        Paths.get("/tmp/wt"),
                        128,
                        "fatal: bad ref");
        final String msg = ex.getMessage();
        assertThat(msg).contains("BASELINE");
        assertThat(msg).contains("abc123");
        assertThat(msg).contains("/tmp/wt");
        assertThat(msg).contains("128");
        assertThat(msg).contains("bad ref");
    }

    @Test
    void gettersReturnValues() {
        final WorkspacePrepareException ex =
                new WorkspacePrepareException(
                        WorkspaceSide.TARGET,
                        "def456",
                        Paths.get("/p"),
                        1,
                        "error text");
        assertThat(ex.getSide())
                .isEqualTo(
                        WorkspaceSide.TARGET);
        assertThat(ex.getCommit())
                .isEqualTo("def456");
        assertThat(ex.getWorktreePath())
                .isEqualTo(Paths.get("/p"));
        assertThat(ex.getGitExitCode())
                .isEqualTo(1);
        assertThat(ex.getGitStderr())
                .isEqualTo("error text");
    }

    @Test
    void nullPathIsAllowed() {
        final WorkspacePrepareException ex =
                new WorkspacePrepareException(
                        WorkspaceSide.CURRENT,
                        "HEAD",
                        null,
                        0,
                        "");
        assertThat(ex.getWorktreePath())
                .isNull();
    }
}
