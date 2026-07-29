package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticCollector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions
        .assumeTrue;

/**
 * Integration tests for
 * {@link DependencyAnalyzer}.
 */
class DependencyAnalyzerIT {

    @TempDir
    private Path tempDir;

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
    void singleModuleDependencyTree()
            throws Exception {
        final Path projectDir =
                tempDir.resolve("single");
        createSingleModuleProject(
                projectDir);
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "target",
                        projectDir,
                        Set.of(),
                        diag);
        final List<ModuleDependencyTree>
                trees = analyzer.analyze();
        assertThat(trees).hasSize(1);
        final ModuleDependencyTree tree =
                trees.get(0);
        assertThat(tree.getModule()
                .getArtifactId())
                .isEqualTo("single");
        assertThat(tree.getDependencies())
                .isNotEmpty();
    }

    @Test
    void testScopeExcluded()
            throws Exception {
        final Path projectDir =
                tempDir.resolve("withtest");
        createProjectWithTestDep(
                projectDir);
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "target",
                        projectDir,
                        Set.of(),
                        diag);
        final List<ModuleDependencyTree>
                trees = analyzer.analyze();
        assertThat(trees).hasSize(1);
        final ModuleDependencyTree tree =
                trees.get(0);
        assertThat(tree.getDependencies())
                .extracting(n -> n
                        .getArtifact()
                        .getArtifactId())
                .doesNotContain("junit");
    }

    @Test
    void multiModuleDependencyTrees()
            throws Exception {
        final Path projectDir =
                tempDir.resolve("multi");
        createMultiModuleProject(
                projectDir);
        final ArtifactCoord modA =
                new ArtifactCoord(
                        "test", "mod-a",
                        "jar",
                        "1.0-SNAPSHOT");
        final ArtifactCoord modB =
                new ArtifactCoord(
                        "test", "mod-b",
                        "jar",
                        "1.0-SNAPSHOT");
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "baseline",
                        projectDir,
                        Set.of(modA, modB),
                        diag);
        final List<ModuleDependencyTree>
                trees = analyzer.analyze();
        assertThat(trees)
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void reactorModulesExcluded()
            throws Exception {
        final Path projectDir =
                tempDir.resolve("reactor");
        createMultiModuleProject(
                projectDir);
        final ArtifactCoord modA =
                new ArtifactCoord(
                        "test", "mod-a",
                        "jar",
                        "1.0-SNAPSHOT");
        final ArtifactCoord modB =
                new ArtifactCoord(
                        "test", "mod-b",
                        "jar",
                        "1.0-SNAPSHOT");
        final DependencyAnalyzer analyzer =
                new DependencyAnalyzer(
                        "target",
                        projectDir,
                        Set.of(modA, modB),
                        diag);
        final List<ModuleDependencyTree>
                trees = analyzer.analyze();
        for (ModuleDependencyTree tree :
                trees) {
            assertThat(tree.getDependencies())
                    .extracting(n -> n
                            .getArtifact()
                            .getArtifactId())
                    .doesNotContain("mod-a",
                            "mod-b");
        }
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
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>org.slf4j"
                        + "</groupId>"
                        + "<artifactId>"
                        + "slf4j-api"
                        + "</artifactId>"
                        + "<version>"
                        + "2.0.13"
                        + "</version>"
                        + "</dependency>"
                        + "</dependencies>"
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

    private void createProjectWithTestDep(
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
                        + "<artifactId>withtest"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>junit"
                        + "</groupId>"
                        + "<artifactId>junit"
                        + "</artifactId>"
                        + "<version>4.13.2"
                        + "</version>"
                        + "<scope>test"
                        + "</scope>"
                        + "</dependency>"
                        + "</dependencies>"
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
        createChildModuleWithDep(
                dir, "mod-a", "HelloA");
        createChildModuleWithDep(
                dir, "mod-b", "HelloB");
    }

    private void createChildModuleWithDep(
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
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>org.slf4j"
                        + "</groupId>"
                        + "<artifactId>"
                        + "slf4j-api"
                        + "</artifactId>"
                        + "<version>"
                        + "2.0.13"
                        + "</version>"
                        + "</dependency>"
                        + "</dependencies>"
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
}
