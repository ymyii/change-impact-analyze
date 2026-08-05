package io.github.dependencyanalysis.build;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link BuildRunner}
 * constructors.
 */
class BuildRunnerConstructorTest {

    /** Temporary directory for test. */
    @TempDir
    private Path tempDir;

    @Test
    void threeArgConstructorWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final BuildRunner runner =
                new BuildRunner(
                        "test",
                        tempDir,
                        diag);
        assertThat(runner).isNotNull();
    }

    @Test
    void fourArgConstructorWithNullWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final BuildRunner runner =
                new BuildRunner(
                        "test",
                        tempDir,
                        diag,
                        null);
        assertThat(runner).isNotNull();
    }

    @Test
    void fourArgConstructorWithJavaHomeWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final File javaHome =
                new File("/tmp/java");
        final BuildRunner runner =
                new BuildRunner(
                        "test",
                        tempDir,
                        diag,
                        javaHome);
        assertThat(runner).isNotNull();
    }
}
