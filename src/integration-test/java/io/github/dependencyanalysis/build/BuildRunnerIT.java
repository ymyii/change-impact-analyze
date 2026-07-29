package io.github.dependencyanalysis.build;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis
        .diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis
        .diagnostic.DiagnosticLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition
        .EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions
        .assumeTrue;

/**
 * Integration tests for
 * {@link BuildRunner}.
 */
class BuildRunnerIT {

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
    void singleModuleCompileSuccess()
            throws Exception {
        projectDir = tempDir.resolve(
                "single");
        createSingleModuleProject(
                projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        final BuildResult result =
                runner.build();
        assertThat(result.getOutputs())
                .hasSize(1);
        final ModuleBuildOutput mod =
                result.getOutputs().get(0);
        assertThat(mod.getModulePath())
                .isEqualTo(projectDir);
        assertThat(mod.getClassesDir())
                .isEqualTo(projectDir
                        .resolve(
                                "target/classes"));
        assertThat(Files.isDirectory(
                mod.getClassesDir()))
                .isTrue();
    }

    @Test
    void multiModuleCompileSuccess()
            throws Exception {
        projectDir = tempDir.resolve(
                "multi");
        createMultiModuleProject(
                projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "baseline",
                        projectDir,
                        diag);
        final BuildResult result =
                runner.build();
        assertThat(result.getOutputs())
                .hasSizeGreaterThanOrEqualTo(
                        2);
        assertThat(result.getOutputs())
                .extracting(
                        ModuleBuildOutput
                                ::getClassesDir)
                .allSatisfy(p ->
                        assertThat(
                                p.getFileName()
                                        .toString())
                                .isEqualTo(
                                        "classes"))
                .allSatisfy(p ->
                        assertThat(
                                p.getParent()
                                        .getFileName()
                                        .toString())
                                .isEqualTo(
                                        "target"));
    }

    @Test
    void testClassesNotInResult()
            throws Exception {
        projectDir = tempDir.resolve(
                "withtest");
        createSingleModuleProject(
                projectDir);
        addTestSource(projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        final BuildResult result =
                runner.build();
        assertThat(result.getOutputs())
                .extracting(
                        ModuleBuildOutput
                                ::getClassesDir)
                .noneMatch(p ->
                        p.getFileName()
                                .toString()
                                .contains(
                                        "test"));
    }

    @Test
    void compileFailureThrowsBuildException()
            throws Exception {
        projectDir = tempDir.resolve(
                "broken");
        createBrokenProject(projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "baseline",
                        projectDir,
                        diag);
        assertThatThrownBy(runner::build)
                .isInstanceOf(
                        BuildException.class)
                .satisfies(ex -> {
                    final BuildException be =
                            (BuildException) ex;
                    assertThat(be.getSide())
                            .isEqualTo(
                                    "baseline");
                    assertThat(
                            be.getCommand())
                            .contains("mvn");
                    assertThat(
                            be.getExitCode())
                            .isNotZero();
                    assertThat(be.getLogFile())
                            .isNotNull();
                    assertThat(Files.exists(
                            be.getLogFile()))
                            .isTrue();
                });
    }

    @Test
    void failureDiagnosticFields()
            throws Exception {
        projectDir = tempDir.resolve(
                "diagfail");
        createBrokenProject(projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        try {
            runner.build();
        } catch (BuildException ex) {
            assertThat(ex.getSide())
                    .isEqualTo("target");
            assertThat(ex.getModule())
                    .isEqualTo(projectDir
                            .toString());
            assertThat(ex.getStderr())
                    .isNotBlank();
            assertThat(ex.getLogFile())
                    .isNotNull();
        }
    }

    @Test
    void diagnosticEventsEmitted()
            throws Exception {
        projectDir = tempDir.resolve(
                "events");
        createSingleModuleProject(
                projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        runner.build();
        final List<DiagnosticEvent> evts =
                diag.getEvents();
        assertThat(evts)
                .anyMatch(e ->
                        "build".equals(
                                e.getStage())
                        && e.getMessage()
                                .contains(
                                        "started"))
                .anyMatch(e ->
                        "build".equals(
                                e.getStage())
                        && e.getMessage()
                                .contains(
                                        "ended"));
    }

    @Test
    void logFilePersisted() throws Exception {
        projectDir = tempDir.resolve(
                "logtest");
        createSingleModuleProject(
                projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        runner.build();
        final List<DiagnosticEvent> evts =
                diag.getEvents();
        assertThat(evts)
                .anyMatch(e ->
                        "build".equals(
                                e.getStage())
                        && e.getMessage()
                                .contains(
                                        "Found"));
    }

    @Test
    void failureEmitsFailStage()
            throws Exception {
        projectDir = tempDir.resolve(
                "failevt");
        createBrokenProject(projectDir);
        final BuildRunner runner =
                new BuildRunner(
                        "target",
                        projectDir,
                        diag);
        try {
            runner.build();
        } catch (BuildException ex) {
            // expected
        }
        final List<DiagnosticEvent> evts =
                diag.getEvents();
        assertThat(evts)
                .anyMatch(e ->
                        "build".equals(
                                e.getStage())
                        && e.getLevel()
                                == DiagnosticLevel
                                        .ERROR);
    }

    private void createSingleModuleProject(
            final Path dir)
            throws Exception {
        Files.createDirectories(dir);
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>test"
                        + "</groupId>"
                        + "<artifactId>single"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "<properties>"
                        + "<maven.compiler.source>"
                        + "8"
                        + "</maven.compiler.source>"
                        + "<maven.compiler.target>"
                        + "8"
                        + "</maven.compiler.target>"
                        + "</properties>"
                        + "</project>";
        Files.writeString(
                dir.resolve("pom.xml"), pom);
        final Path srcDir = dir.resolve(
                "src/main/java/test");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("Hello.java"),
                "package test;\n"
                        + "public class Hello "
                        + "{\n"
                        + "  public String "
                        + "greet() {\n"
                        + "    return \"hi\";\n"
                        + "  }\n"
                        + "}\n");
    }

    private void createMultiModuleProject(
            final Path dir)
            throws Exception {
        Files.createDirectories(dir);
        final String parentPom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>test"
                        + "</groupId>"
                        + "<artifactId>parent"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "<packaging>pom"
                        + "</packaging>"
                        + "<modules>"
                        + "<module>mod-a"
                        + "</module>"
                        + "<module>mod-b"
                        + "</module>"
                        + "</modules>"
                        + "<properties>"
                        + "<maven.compiler.source>"
                        + "8"
                        + "</maven.compiler.source>"
                        + "<maven.compiler.target>"
                        + "8"
                        + "</maven.compiler.target>"
                        + "</properties>"
                        + "</project>";
        Files.writeString(
                dir.resolve("pom.xml"),
                parentPom);
        createChildModule(dir, "mod-a",
                "HelloA");
        createChildModule(dir, "mod-b",
                "HelloB");
    }

    private void createChildModule(
            final Path parent,
            final String name,
            final String className)
            throws Exception {
        final Path modDir =
                parent.resolve(name);
        Files.createDirectories(modDir);
        final String childPom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<parent>"
                        + "<groupId>test"
                        + "</groupId>"
                        + "<artifactId>parent"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "</parent>"
                        + "<artifactId>"
                        + name
                        + "</artifactId>"
                        + "</project>";
        Files.writeString(
                modDir.resolve("pom.xml"),
                childPom);
        final Path srcDir = modDir.resolve(
                "src/main/java/test");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve(className
                        + ".java"),
                "package test;\n"
                        + "public class "
                        + className + " {\n"
                        + "  public String "
                        + "greet() {\n"
                        + "    return \"hi\";\n"
                        + "  }\n"
                        + "}\n");
    }

    private void createBrokenProject(
            final Path dir)
            throws Exception {
        Files.createDirectories(dir);
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>test"
                        + "</groupId>"
                        + "<artifactId>broken"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "<properties>"
                        + "<maven.compiler.source>"
                        + "8"
                        + "</maven.compiler.source>"
                        + "<maven.compiler.target>"
                        + "8"
                        + "</maven.compiler.target>"
                        + "</properties>"
                        + "</project>";
        Files.writeString(
                dir.resolve("pom.xml"), pom);
        final Path srcDir = dir.resolve(
                "src/main/java/test");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("Broken.java"),
                "package test;\n"
                        + "public class Broken "
                        + "{\n"
                        + "  public void x() "
                        + "{\n"
                        + "    this is not "
                        + "valid java;\n"
                        + "  }\n"
                        + "}\n");
    }

    private void addTestSource(
            final Path dir)
            throws Exception {
        final Path testDir = dir.resolve(
                "src/test/java/test");
        Files.createDirectories(testDir);
        Files.writeString(
                testDir.resolve(
                        "HelloTest.java"),
                "package test;\n"
                        + "public class "
                        + "HelloTest {\n"
                        + "  public void "
                        + "test() {}\n"
                        + "}\n");
    }
}
