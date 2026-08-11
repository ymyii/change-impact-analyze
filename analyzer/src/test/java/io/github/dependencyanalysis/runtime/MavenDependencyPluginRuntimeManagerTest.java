package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Embedded Maven Plugin repository runtime tests. */
class MavenDependencyPluginRuntimeManagerTest {

    /** Concurrent preparation count. */
    private static final int CONCURRENT_PREPARATIONS = 4;

    /** Dependency Evidence Plugin version supplied by the build. */
    private static final String ARTIFACT_PATH_PLUGIN_VERSION =
            System.getProperty("cia.artifactPathPluginVersion");

    /** Temporary config root. */
    @TempDir
    private Path temporary;

    @Test
    void preparesTwoRepositoriesAndCommandScopedSettings() throws Exception {
        final Path config = temporary.resolve("config");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();
        final MavenDependencyPluginRuntime first = manager.prepare(
                config, List.of("-DskipTests"), null);
        final MavenDependencyPluginRuntime second = manager.prepare(
                config, List.of("-DskipTests"), "");
        final Path firstSettings = settings(first);
        final Path secondSettings = settings(second);

        assertThat(first.isEmbedded()).isTrue();
        assertThat(first.getVersion()).isEqualTo("3.6.1");
        assertThat(first.getGoal()).isEqualTo(
                "org.apache.maven.plugins:"
                        + "maven-dependency-plugin:3.6.1:tree");
        assertThat(first.getGoal("list")).endsWith(":3.6.1:list");
        assertThat(first.getDependencyEvidenceGoal()).isEqualTo(
                "io.github.dependencyanalysis:"
                        + "dependency-analyzer-artifact-path-maven-plugin:"
                        + ARTIFACT_PATH_PLUGIN_VERSION
                        + ":collect-dependency-evidence");
        assertThat(first.getRepositories()).hasSize(2);
        assertThat(dependencyJar(first)).isRegularFile();
        assertThat(artifactPathJar(first)).isRegularFile();
        assertThat(first.getRepositories().get(0))
                .isEqualTo(second.getRepositories().get(0));
        if (isSnapshot()) {
            assertThat(first.getRepositories().get(1))
                    .isNotEqualTo(second.getRepositories().get(1));
            assertThat(first.getMavenArguments()).contains("-U");
        } else {
            assertThat(first.getRepositories().get(1))
                    .isEqualTo(second.getRepositories().get(1));
            assertThat(first.getMavenArguments()).doesNotContain("-U");
        }
        assertThat(firstSettings).isNotEqualTo(secondSettings);
        assertThat(firstSettings).content()
                .contains(first.getRepositories().get(0)
                        .toUri().toASCIIString())
                .contains(first.getRepositories().get(1)
                        .toUri().toASCIIString())
                .contains("dependency-analyzer-maven-dependency-plugin-3.6.1")
                .contains("dependency-analyzer-dependency-analyzer-artifact-"
                        + "path-maven-plugin-"
                        + ARTIFACT_PATH_PLUGIN_VERSION.toLowerCase())
                .doesNotContain("checksumPolicy");

        first.close();
        second.close();
        assertThat(firstSettings).doesNotExist();
        assertThat(secondSettings).doesNotExist();
    }

    @Test
    void mergesGlobalSettingsAndExcludesBothRepositoriesFromMirror()
            throws Exception {
        final Path global = temporary.resolve("global-settings.xml");
        Files.writeString(global, """
                <?xml version="1.0" encoding="UTF-8"?>
                <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
                  <mirrors>
                    <mirror>
                      <id>company</id>
                      <url>https://repo.example.test/maven</url>
                      <mirrorOf>*</mirrorOf>
                    </mirror>
                  </mirrors>
                </settings>
                """);
        final Path user = temporary.resolve("user-settings.xml");
        Files.writeString(user, "<settings/>");

        try (MavenDependencyPluginRuntime runtime =
                     new MavenDependencyPluginRuntimeManager().prepare(
                             temporary.resolve("merge"),
                             List.of("-gs", global.toString(),
                                     "-s", user.toString(), "-Pdev"),
                             null)) {
            assertThat(runtime.getMavenArguments())
                    .containsSubsequence("-s", user.toString())
                    .contains("-Pdev")
                    .doesNotContain(global.toString());
            assertThat(settings(runtime)).content()
                    .contains("https://repo.example.test/maven")
                    .contains("!dependency-analyzer-maven-dependency-"
                            + "plugin-3.6.1")
                    .contains("!dependency-analyzer-dependency-analyzer-"
                            + "artifact-path-maven-plugin-");
        }
    }

    @Test
    void missingRequiredFileRebuildsWithoutContentFingerprinting()
            throws Exception {
        final Path config = temporary.resolve("rebuild");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();
        final MavenDependencyPluginRuntime first = manager.prepare(
                config, List.of(), null);
        final Path plugin = dependencyJar(first);
        final long expectedSize = Files.size(plugin);
        Files.delete(plugin);

        final MavenDependencyPluginRuntime rebuilt = manager.prepare(
                config, List.of(), null);
        assertThat(Files.size(dependencyJar(rebuilt)))
                .isEqualTo(expectedSize);

        Files.writeString(dependencyJar(rebuilt), "damaged-but-present");
        final MavenDependencyPluginRuntime reused = manager.prepare(
                config, List.of(), null);
        assertThat(dependencyJar(reused)).hasContent("damaged-but-present");

        first.close();
        rebuilt.close();
        reused.close();
    }

    @Test
    void concurrentPreparationKeepsStableRepositoryConsistent()
            throws Exception {
        final Path config = temporary.resolve("concurrent");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();
        final ExecutorService executor = Executors.newFixedThreadPool(
                CONCURRENT_PREPARATIONS);
        final List<MavenDependencyPluginRuntime> runtimes =
                new ArrayList<>();
        try {
            final List<java.util.concurrent.Callable<
                    MavenDependencyPluginRuntime>> tasks = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_PREPARATIONS; index++) {
                tasks.add(() -> manager.prepare(config, List.of(), null));
            }
            final List<Future<MavenDependencyPluginRuntime>> futures =
                    executor.invokeAll(tasks);
            for (Future<MavenDependencyPluginRuntime> future : futures) {
                runtimes.add(future.get());
            }
            assertThat(runtimes.stream()
                    .map(runtime -> runtime.getRepositories().get(0))
                    .toList()).containsOnly(
                    runtimes.get(0).getRepositories().get(0));
            assertThat(runtimes).allMatch(runtime ->
                    Files.isRegularFile(dependencyJar(runtime))
                            && Files.isRegularFile(artifactPathJar(runtime)));
            if (isSnapshot()) {
                assertThat(runtimes.stream()
                        .map(runtime -> runtime.getRepositories().get(1))
                        .distinct().count())
                        .isEqualTo(CONCURRENT_PREPARATIONS);
            }
        } finally {
            for (MavenDependencyPluginRuntime runtime : runtimes) {
                runtime.close();
            }
            executor.shutdownNow();
        }
    }

    @Test
    void overrideKeepsRepositoriesAndBlocksOldCapability() throws Exception {
        try (MavenDependencyPluginRuntime runtime =
                     new MavenDependencyPluginRuntimeManager().prepare(
                             temporary.resolve("override"),
                             List.of("-Pdev"), "3.5.0")) {
            assertThat(runtime.getRepositories()).hasSize(2);
            assertThat(runtime.getMavenArguments()).contains("-Pdev", "-gs");
            assertThat(runtime.getGoal()).endsWith(":3.5.0:tree");
        }
        assertThatThrownBy(() ->
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("blocked"), List.of(), "2.8"))
                .isInstanceOf(MavenRuntimeException.class)
                .hasMessageContaining("does not provide complete");
        assertThat(MavenDependencyPluginRuntimeManager
                .supportsCompleteEvidence("3.6.1:help")).isFalse();
    }

    @Test
    void executesStructuredEvidenceGoalWithExternalMirrorBlocked()
            throws Exception {
        final Path config = temporary.resolve("execution");
        final Path project = temporary.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>offline-plugin</artifactId>
                  <version>1</version>
                </project>
                """);
        final Path global = temporary.resolve("blocked.xml");
        Files.writeString(global, """
                <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
                  <mirrors>
                    <mirror>
                      <id>blocked</id>
                      <url>http://127.0.0.1:1/unavailable</url>
                      <mirrorOf>*</mirrorOf>
                    </mirror>
                  </mirrors>
                </settings>
                """);
        final Path evidence = temporary.resolve("evidence cache 中文");
        final String owner = "runtime-manager-test-owner";
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve(".cia-evidence-owner"), owner);
        final MavenRuntimeDescriptor maven = new MavenRuntimeManager()
                .prepare(null, config, null);
        try (MavenDependencyPluginRuntime plugin =
                     new MavenDependencyPluginRuntimeManager().prepare(
                             config,
                             List.of("-gs", global.toString(),
                                     "-Dmaven.repo.local="
                                             + temporary.resolve(
                                             "empty-local")),
                             null)) {
            final List<String> arguments = new ArrayList<>(
                    plugin.getMavenArguments());
            arguments.addAll(List.of(
                    "-X", "-B", "-f", project.resolve("pom.xml").toString(),
                    plugin.getDependencyEvidenceGoal(),
                    "-Dcia.dependencyEvidenceDirectory=" + evidence,
                    "-Dcia.dependencyEvidenceOwner=" + owner));

            final MavenExecutionResult result = new MavenExecutor().execute(
                    maven, project, arguments);

            assertThat(result.getExitCode())
                    .describedAs(result.getCombinedOutput()).isZero();
            assertThat(result.getCombinedOutput())
                    .contains("collect-dependency-evidence")
                    .contains("Dependency Evidence Plugin implementation="
                            + "dependency-evidence-v3")
                    .contains("version=" + ARTIFACT_PATH_PLUGIN_VERSION)
                    .doesNotContain("sha512=");
            final List<Path> evidenceFiles;
            try (var files = Files.list(evidence)) {
                evidenceFiles = files
                        .filter(path -> path.getFileName().toString()
                                .startsWith("module-"))
                        .toList();
            }
            assertThat(evidenceFiles).hasSize(1);
            assertThat(evidenceFiles.get(0)).content()
                    .contains("\"schemaVersion\" : 3")
                    .contains("\"module\"")
                    .contains("\"occurrenceGraph\"")
                    .contains("\"artifacts\" : [ ]");
            assertThat(project.resolve("tree.graphml")).doesNotExist();
            assertThat(project.resolve("artifacts.json")).doesNotExist();
        }
    }

    private Path dependencyJar(
            final MavenDependencyPluginRuntime runtime) {
        return runtime.getRepositories().get(0).resolve(
                "org/apache/maven/plugins/maven-dependency-plugin/3.6.1/"
                        + "maven-dependency-plugin-3.6.1.jar");
    }

    private Path artifactPathJar(
            final MavenDependencyPluginRuntime runtime) {
        return runtime.getRepositories().get(1).resolve(
                "io/github/dependencyanalysis/"
                        + "dependency-analyzer-artifact-path-maven-plugin/"
                        + ARTIFACT_PATH_PLUGIN_VERSION + "/"
                        + "dependency-analyzer-artifact-path-maven-plugin-"
                        + ARTIFACT_PATH_PLUGIN_VERSION + ".jar");
    }

    private boolean isSnapshot() {
        return ARTIFACT_PATH_PLUGIN_VERSION.endsWith("-SNAPSHOT");
    }

    private Path settings(final MavenDependencyPluginRuntime runtime) {
        final List<String> arguments = runtime.getMavenArguments();
        final int option = arguments.indexOf("-gs");
        assertThat(option).isNotNegative();
        return Path.of(arguments.get(option + 1));
    }
}
