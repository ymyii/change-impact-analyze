package io.github.dependencyanalysis.build;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticCollector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.junit.jupiter.api.Assumptions
        .assumeTrue;

/**
 * Integration tests for
 * {@link BuildRunner} with
 * buildJavaHome.
 */
class BuildRunnerJavaHomeIT {

    @TempDir
    private Path tempDir;

    /** Project directory. */
    private Path projectDir;

    /** Diagnostic collector. */
    private DiagnosticCollector diag;

    @BeforeAll
    static void checkMvn() throws Exception {
        final ProcessBuilder pb =
                new ProcessBuilder(
                        "mvn", "--version");
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        final int code = p.waitFor();
        assumeTrue(code == 0,
                "Maven not available");
    }

    @BeforeEach
    void setUpDiag() {
        diag = new DiagnosticCollector(
                new PrintStream(
                        new ByteArrayOutputStream()),
                new PrintStream(
                        new ByteArrayOutputStream()));
    }

    @Test
    void buildWithNullJavaHomeSucceeds()
            throws Exception {
        projectDir = tempDir.resolve(
                "null-java-home");
        createSingleModuleProject(
                projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag,
                        null);
        final BuildResult result =
                runner.build();
        assertThat(result.getOutputs())
                .hasSize(1);
    }

    @Test
    void buildWithCurrentJavaHomeSucceeds()
            throws Exception {
        projectDir = tempDir.resolve(
                "current-java-home");
        createSingleModuleProject(
                projectDir);
        final String javaHome =
                System.getProperty(
                        "java.home");
        final File javaHomeFile =
                new File(javaHome);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag,
                        javaHomeFile);
        final BuildResult result =
                runner.build();
        assertThat(result.getOutputs())
                .hasSize(1);
    }

    private void createSingleModuleProject(
            final Path dir)
            throws Exception {
        Files.createDirectories(dir);
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>\n"
                        + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                        + "    <modelVersion>4.0.0</modelVersion>\n"
                        + "    <groupId>test</groupId>\n"
                        + "    <artifactId>single</artifactId>\n"
                        + "    <version>1.0</version>\n"
                        + "</project>\n";
        Files.writeString(
                dir.resolve("pom.xml"),
                pom);
        final Path srcDir =
                dir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("Main.java"),
                "public class Main {}\n");
    }
}
