package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntimeManager;
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
import java.util.jar.JarOutputStream;

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

    @Test
    void resolvedArtifactsExcludeCleanReactorDependencies()
            throws Exception {
        final Path projectDir = tempDir.resolve("clean-reactor");
        createMultiModuleProject(projectDir);
        addReactorDependency(projectDir.resolve("mod-b/pom.xml"));
        assertThat(projectDir.resolve("mod-a/target/classes"))
                .doesNotExist();
        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of()))
                .analyzeResolved();

        assertThat(result.getArtifacts())
                .extracting(item -> item.getArtifact().getArtifactId())
                .contains("slf4j-api")
                .doesNotContain("mod-a", "mod-b");
        assertThat(result.getArtifacts()).allSatisfy(item -> {
            assertThat(item.getPath()).isAbsolute();
            assertThat(item.getPath()).exists();
        });
        result.getTrees().stream()
                .filter(tree -> tree.getModule().getArtifactId()
                        .startsWith("mod-"))
                .forEach(tree -> assertThat(result.artifactsFor(
                        tree.getModulePath()))
                        .extracting(item -> item.getArtifact()
                                .getArtifactId())
                        .containsExactly("slf4j-api"));
        assertThat(projectDir.resolve("mod-a/target/classes"))
                .doesNotExist();
    }

    @Test
    void resolvedArtifactsHonorExclusionsWithoutMissingDownloads()
            throws Exception {
        final Path repository = tempDir.resolve("fixture-repository");
        final Path rootArtifact = installExcludedDependencyFixture(
                repository);
        final Path projectDir = tempDir.resolve("excluded-project");
        createExcludedDependencyProject(projectDir, repository);
        final Path localRepository = tempDir.resolve("empty-local");
        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of(
                        "-Dmaven.repo.local=" + localRepository)))
                .analyzeResolved();

        assertThat(result.getArtifacts())
                .extracting(item -> item.getArtifact().getArtifactId())
                .containsExactly("root-lib");
        assertThat(result.getArtifacts()).singleElement()
                .extracting(ResolvedArtifact::getPath)
                .isEqualTo(localRepository.resolve(
                        "fixture/repo/root-lib/1/root-lib-1.jar")
                        .toRealPath());
        assertThat(rootArtifact).isRegularFile();
        if (Files.exists(localRepository)) {
            try (java.util.stream.Stream<Path> paths =
                         Files.walk(localRepository)) {
                assertThat(paths.map(Path::toString).toList())
                        .noneMatch(value -> value.contains(
                                "excluded-lib"));
            }
        }
    }

    @Test
    void resolvedArtifactsSkipMissingTestBinary()
            throws Exception {
        final Path repository = tempDir.resolve("scope-repository");
        installFixtureArtifact(repository,
                "compile-lib", "1", "", true);
        installFixtureArtifact(repository,
                "test-lib", "1", "", false);
        final Path projectDir = tempDir.resolve("scope-project");
        createScopeProject(projectDir, repository);
        final Path localRepository = tempDir.resolve("scope-local");

        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of(
                        "-Dmaven.repo.local=" + localRepository)))
                .analyzeResolved();

        assertThat(result.getArtifacts())
                .extracting(item -> item.getArtifact().getArtifactId())
                .containsExactly("compile-lib");
        assertThat(localRepository.resolve(
                "fixture/repo/compile-lib/1/compile-lib-1.jar"))
                .isRegularFile();
        assertThat(localRepository.resolve(
                "fixture/repo/test-lib/1/test-lib-1.jar"))
                .doesNotExist();
        assertThat(localRepository.resolve(
                "fixture/repo/test-lib/1/test-lib-1.jar.lastUpdated"))
                .doesNotExist();
    }

    @Test
    void resolvedArtifactsPreserveAbsoluteSystemPath()
            throws Exception {
        final Path projectDir = tempDir.resolve("system-project");
        final Path systemJar = projectDir.resolve(
                "system libraries/system-lib-1.jar");
        Files.createDirectories(systemJar.getParent());
        try (JarOutputStream ignored = new JarOutputStream(
                Files.newOutputStream(systemJar))) {
            // Empty but valid JAR fixture.
        }
        createSystemScopeProject(projectDir, systemJar);

        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of(
                        "-Dmaven.repo.local="
                                + tempDir.resolve("system-local"))))
                .analyzeResolved();

        assertThat(result.getArtifacts()).singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getArtifact().getArtifactId())
                            .isEqualTo("system-lib");
                    assertThat(artifact.getPath())
                            .isEqualTo(systemJar.toRealPath());
                });
    }

    @Test
    void resolvedArtifactsRequestOnlyMediatedWinner()
            throws Exception {
        final Path repository = tempDir.resolve("mediation-repository");
        installMediationFixture(repository);
        final Path projectDir = tempDir.resolve("mediation-project");
        createMediationProject(projectDir, repository);
        final Path localRepository = tempDir.resolve("mediation-local");
        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of(
                        "-Dmaven.repo.local=" + localRepository)))
                .analyzeResolved();

        assertThat(result.getArtifacts())
                .extracting(ResolvedArtifact::getArtifact)
                .filteredOn(item -> item.getArtifactId().equals("selected"))
                .extracting(ArtifactCoord::getVersion)
                .containsExactly("1");
        assertThat(localRepository.resolve(
                "fixture/repo/selected/1/selected-1.jar"))
                .isRegularFile();
        assertThat(localRepository.resolve(
                "fixture/repo/selected/2/selected-2.jar"))
                .doesNotExist();
        assertThat(localRepository.resolve(
                "fixture/repo/selected/2/selected-2.jar.lastUpdated"))
                .doesNotExist();
    }

    @Test
    void resolvedArtifactsPreserveClassifierAndArtifactType()
            throws Exception {
        final Path repository = tempDir.resolve(
                "artifact repository with spaces");
        installTypedArtifactFixture(repository);
        final Path projectDir = tempDir.resolve("typed-project");
        createTypedArtifactProject(projectDir, repository);
        final DependencyAnalysisResult result = new DependencyAnalyzer(
                "baseline", projectDir, Set.of(), diag)
                .withPluginRuntime(pluginRuntime(List.of(
                        "-Dmaven.repo.local="
                                + tempDir.resolve("typed-local"))))
                .analyzeResolved();

        assertThat(result.getArtifacts())
                .filteredOn(item -> item.getArtifact()
                        .getArtifactId().equals("testable"))
                .singleElement().satisfies(item -> {
                    assertThat(item.getArtifact().getType())
                            .isEqualTo("test-jar");
                    assertThat(item.getArtifact().getClassifier())
                            .isEqualTo("tests");
                    assertThat(item.getPath().getFileName().toString())
                            .isEqualTo("testable-1-tests.jar");
                });
        assertThat(result.getArtifacts())
                .filteredOn(item -> item.getArtifact()
                        .getArtifactId().equals("special"))
                .singleElement().satisfies(item -> {
                    assertThat(item.getArtifact().getType())
                            .isEqualTo("zip");
                    assertThat(item.getArtifact().getClassifier())
                            .isEqualTo("tests");
                    assertThat(item.getPath().getFileName().toString())
                            .isEqualTo("special-1-tests.zip");
                });
    }

    private MavenDependencyPluginRuntime pluginRuntime(
            final List<String> arguments) {
        return new MavenDependencyPluginRuntimeManager().prepare(
                tempDir.resolve("plugin-runtime"), arguments, null);
    }

    private Path installExcludedDependencyFixture(
            final Path repository) throws Exception {
        final Path directory = repository.resolve(
                "fixture/repo/root-lib/1");
        Files.createDirectories(directory);
        final Path jar = directory.resolve("root-lib-1.jar");
        Files.write(jar, new byte[]{0});
        Files.writeString(directory.resolve("root-lib-1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.repo</groupId>
                  <artifactId>root-lib</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>excluded-lib</artifactId>
                      <version>99</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        return jar;
    }

    private void createExcludedDependencyProject(
            final Path directory,
            final Path repository) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>excluded-project</artifactId>
                  <version>1</version>
                  <repositories>
                    <repository>
                      <id>fixture</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>root-lib</artifactId>
                      <version>1</version>
                      <exclusions>
                        <exclusion>
                          <groupId>fixture.repo</groupId>
                          <artifactId>excluded-lib</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(repository.toUri().toASCIIString()));
    }

    private void installMediationFixture(final Path repository)
            throws Exception {
        installFixtureArtifact(repository, "selected", "1", "", true);
        installFixtureArtifact(repository, "selected", "2", "", false);
        installFixtureArtifact(repository, "branch-a", "1", """
                <dependency>
                  <groupId>fixture.repo</groupId>
                  <artifactId>selected</artifactId>
                  <version>1</version>
                </dependency>
                """, true);
        installFixtureArtifact(repository, "branch-b", "1", """
                <dependency>
                  <groupId>fixture.repo</groupId>
                  <artifactId>selected</artifactId>
                  <version>2</version>
                </dependency>
                """, true);
    }

    private void installFixtureArtifact(
            final Path repository,
            final String artifactId,
            final String version,
            final String dependencies,
            final boolean withJar) throws Exception {
        final Path directory = repository.resolve(
                "fixture/repo/" + artifactId + "/" + version);
        Files.createDirectories(directory);
        if (withJar) {
            Files.write(directory.resolve(
                    artifactId + "-" + version + ".jar"), new byte[]{0});
        }
        Files.writeString(directory.resolve(
                artifactId + "-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.repo</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <dependencies>
                    %s
                  </dependencies>
                </project>
                """.formatted(artifactId, version, dependencies));
    }

    private void createMediationProject(
            final Path directory,
            final Path repository) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>mediation-project</artifactId>
                  <version>1</version>
                  <repositories>
                    <repository>
                      <id>fixture</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>branch-a</artifactId>
                      <version>1</version>
                    </dependency>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>branch-b</artifactId>
                      <version>1</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(repository.toUri().toASCIIString()));
    }

    private void createScopeProject(
            final Path directory,
            final Path repository) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>scope-project</artifactId>
                  <version>1</version>
                  <repositories>
                    <repository>
                      <id>fixture</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>compile-lib</artifactId>
                      <version>1</version>
                    </dependency>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>test-lib</artifactId>
                      <version>1</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(repository.toUri().toASCIIString()));
    }

    private void createSystemScopeProject(
            final Path directory,
            final Path systemJar) throws Exception {
        Files.createDirectories(directory);
        final String escapedPath = systemJar.toAbsolutePath().normalize()
                .toString().replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>system-project</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.system</groupId>
                      <artifactId>system-lib</artifactId>
                      <version>1</version>
                      <scope>system</scope>
                      <systemPath>%s</systemPath>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(escapedPath));
    }

    private void installTypedArtifactFixture(final Path repository)
            throws Exception {
        final Path testJar = repository.resolve(
                "fixture/repo/testable/1");
        Files.createDirectories(testJar);
        Files.write(testJar.resolve("testable-1-tests.jar"),
                new byte[]{0});
        Files.writeString(testJar.resolve("testable-1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.repo</groupId>
                  <artifactId>testable</artifactId>
                  <version>1</version>
                </project>
                """);
        final Path zip = repository.resolve("fixture/repo/special/1");
        Files.createDirectories(zip);
        Files.write(zip.resolve("special-1-tests.zip"), new byte[]{0});
        Files.writeString(zip.resolve("special-1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.repo</groupId>
                  <artifactId>special</artifactId>
                  <version>1</version>
                </project>
                """);
    }

    private void createTypedArtifactProject(
            final Path directory,
            final Path repository) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>typed-project</artifactId>
                  <version>1</version>
                  <repositories>
                    <repository>
                      <id>fixture</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                  <dependencies>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>testable</artifactId>
                      <version>1</version>
                      <type>test-jar</type>
                    </dependency>
                    <dependency>
                      <groupId>fixture.repo</groupId>
                      <artifactId>special</artifactId>
                      <version>1</version>
                      <type>zip</type>
                      <classifier>tests</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(repository.toUri().toASCIIString()));
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

    private void addReactorDependency(final Path pom) throws Exception {
        final String dependency = "<dependency>"
                + "<groupId>test</groupId>"
                + "<artifactId>mod-a</artifactId>"
                + "<version>1.0-SNAPSHOT</version>"
                + "</dependency>";
        Files.writeString(pom, Files.readString(pom)
                .replace("</dependencies>",
                        dependency + "</dependencies>"));
    }
}
