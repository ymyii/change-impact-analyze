package io.github.dependencyanalysis.jar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link JarLocatorException}.
 */
class JarLocatorExceptionTest {

    @Test
    void messageContainsDiagnosticInfo() {
        final Path exp =
                Path.of("/repo/g/a/1.0"
                        + "/a-1.0.jar");
        final Path repo =
                Path.of("/repo");
        final JarLocatorException ex =
                new JarLocatorException(
                        "g:a:jar:1.0",
                        "old", exp, repo);
        final String msg = ex.getMessage();
        assertThat(msg)
                .contains("g:a:jar:1.0");
        assertThat(msg).contains("old");
        assertThat(msg)
                .contains(exp.toString());
        assertThat(msg)
                .contains(repo.toString());
    }

    @Test
    void gettersReturnCorrectValues() {
        final Path exp =
                Path.of("/repo/g/a/1.0"
                        + "/a-1.0.jar");
        final Path repo =
                Path.of("/repo");
        final JarLocatorException ex =
                new JarLocatorException(
                        "g:a:jar:1.0",
                        "new", exp, repo);
        assertThat(ex.getArtifactCoord())
                .isEqualTo("g:a:jar:1.0");
        assertThat(ex.getSide())
                .isEqualTo("new");
        assertThat(ex.getExpectedPath())
                .isEqualTo(exp);
        assertThat(ex.getRepositoryRoot())
                .isEqualTo(repo);
    }
}
