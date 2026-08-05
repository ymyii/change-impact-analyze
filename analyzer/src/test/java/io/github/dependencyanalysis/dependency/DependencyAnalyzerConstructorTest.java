package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link DependencyAnalyzer}
 * constructors.
 */
class DependencyAnalyzerConstructorTest {

    /** Temporary directory for test. */
    @TempDir
    private Path tempDir;

    @Test
    void fourArgConstructorWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "test",
                        tempDir,
                        Set.of(),
                        diag);
        assertThat(analyzer).isNotNull();
    }

    @Test
    void fiveArgConstructorWithNullWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "test",
                        tempDir,
                        Set.of(),
                        diag,
                        null);
        assertThat(analyzer).isNotNull();
    }

    @Test
    void fiveArgConstructorWithJavaHomeWorks() {
        final DiagnosticLog diag = new DiagnosticLog();
        final File javaHome =
                new File("/tmp/java");
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "test",
                        tempDir,
                        Set.of(),
                        diag,
                        javaHome);
        assertThat(analyzer).isNotNull();
    }
}
