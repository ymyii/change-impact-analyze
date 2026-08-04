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

/** Embedded Maven Dependency Plugin runtime tests. */
class MavenDependencyPluginRuntimeManagerTest {

    /** Concurrent preparation count. */
    private static final int CONCURRENT_PREPARATIONS = 4;

    /** Artifact Path Plugin version supplied by the build. */
    private static final String ARTIFACT_PATH_PLUGIN_VERSION =
            System.getProperty("cia.artifactPathPluginVersion");

    /** Temporary config root. */
    @TempDir
    private Path temporary;

    @Test
    void extractsVerifiedRepositoryAndReusesSettings()
            throws Exception {
        final Path config = temporary.resolve("config");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();

        final MavenDependencyPluginRuntime first =
                manager.prepare(config,
                        List.of("-DskipTests"), null);
        final Path settings = settings(first);
        final long modified = Files.getLastModifiedTime(
                settings).toMillis();
        final MavenDependencyPluginRuntime second =
                manager.prepare(config,
                        List.of("-DskipTests"), "");

        assertThat(first.isEmbedded()).isTrue();
        assertThat(first.getVersion()).isEqualTo("3.6.1");
        assertThat(first.getGoal()).isEqualTo(
                "org.apache.maven.plugins:"
                        + "maven-dependency-plugin:3.6.1:tree");
        assertThat(first.getGoal("list")).isEqualTo(
                "org.apache.maven.plugins:"
                        + "maven-dependency-plugin:3.6.1:list");
        assertThat(first.getArtifactPathGoal()).isEqualTo(
                "io.github.dependencyanalysis:"
                        + "dependency-analyzer-artifact-path-maven-plugin:"
                        + ARTIFACT_PATH_PLUGIN_VERSION
                        + ":resolve-artifact-paths");
        assertThat(first.getArtifactPathPluginVersion())
                .isEqualTo(ARTIFACT_PATH_PLUGIN_VERSION);
        assertThat(first.getArtifactPathJarSha512())
                .matches("[0-9a-f]{128}");
        assertThat(first.getRepositorySha512())
                .matches("[0-9a-f]{128}");
        assertThat(first.getRepository().resolve(
                "org/apache/maven/plugins/"
                        + "maven-dependency-plugin/3.6.1/"
                        + "maven-dependency-plugin-3.6.1.jar"))
                .isRegularFile();
        assertThat(first.getRepository().resolve(
                "io/github/dependencyanalysis/"
                        + "dependency-analyzer-artifact-path-maven-plugin/"
                        + ARTIFACT_PATH_PLUGIN_VERSION + "/"
                        + "dependency-analyzer-artifact-path-maven-plugin-"
                        + ARTIFACT_PATH_PLUGIN_VERSION + ".jar"))
                .isRegularFile();
        assertThat(settings).content()
                .contains(first.getRepository().toUri()
                        .toASCIIString())
                .contains("<pluginRepositories>")
                .contains("<updatePolicy>always</updatePolicy>")
                .contains("<checksumPolicy>fail</checksumPolicy>");
        assertThat(settings(second)).isEqualTo(settings);
        assertThat(Files.getLastModifiedTime(
                settings(second)).toMillis())
                .isEqualTo(modified);

        Files.writeString(settings, "damaged");
        final MavenDependencyPluginRuntime repaired =
                manager.prepare(config,
                        List.of("-DskipTests"), null);
        assertThat(settings(repaired)).content()
                .contains("<pluginRepositories>");
    }

    @Test
    void mergesGlobalSettingsAndPreservesUserSettingsOption()
            throws Exception {
        final Path global = temporary.resolve(
                "global-settings.xml");
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
        final Path user = temporary.resolve(
                "user-settings.xml");
        Files.writeString(user, "<settings/>");

        final MavenDependencyPluginRuntime runtime =
                new MavenDependencyPluginRuntimeManager()
                        .prepare(temporary.resolve("merge"),
                                List.of("-gs", global.toString(),
                                        "-s", user.toString(),
                                        "-Pdev"), null);

        assertThat(runtime.getMavenArguments())
                .containsSubsequence("-s", user.toString())
                .contains("-Pdev")
                .doesNotContain(global.toString());
        assertThat(settings(runtime)).content()
                .contains("https://repo.example.test/maven")
                .contains(",!dependency-analyzer-plugin-")
                .contains("dependency-analyzer-plugin-");
    }

    @Test
    void damagedRepositoryRebuildsAndPreservesUnknownFiles()
            throws Exception {
        final Path config = temporary.resolve("damaged");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();
        final MavenDependencyPluginRuntime first =
                manager.prepare(config, List.of(), null);
        final Path plugin = first.getRepository().resolve(
                "org/apache/maven/plugins/"
                        + "maven-dependency-plugin/3.6.1/"
                        + "maven-dependency-plugin-3.6.1.jar");
        final long expectedSize = Files.size(plugin);
        Files.writeString(plugin, "damaged");
        final Path unknown = config.resolve("user.txt");
        Files.writeString(unknown, "keep");

        final MavenDependencyPluginRuntime rebuilt =
                manager.prepare(config, List.of(), null);

        assertThat(Files.size(rebuilt.getRepository().resolve(
                first.getRepository().relativize(plugin))))
                .isEqualTo(expectedSize);
        assertThat(unknown).hasContent("keep");
    }

    @Test
    void concurrentPreparationPublishesOneCompleteRepository()
            throws Exception {
        final Path config = temporary.resolve("concurrent");
        final MavenDependencyPluginRuntimeManager manager =
                new MavenDependencyPluginRuntimeManager();
        final ExecutorService executor =
                Executors.newFixedThreadPool(
                        CONCURRENT_PREPARATIONS);
        try {
            final List<java.util.concurrent.Callable<
                    MavenDependencyPluginRuntime>> tasks =
                    new ArrayList<>();
            for (int index = 0;
                 index < CONCURRENT_PREPARATIONS; index++) {
                tasks.add(() -> manager.prepare(
                        config, List.of(), null));
            }
            final List<Future<
                    MavenDependencyPluginRuntime>> futures =
                    executor.invokeAll(tasks);
            final List<Path> repositories =
                    new ArrayList<>();
            for (Future<MavenDependencyPluginRuntime> future
                    : futures) {
                repositories.add(future.get()
                        .getRepository());
            }

            assertThat(repositories)
                    .containsOnly(repositories.get(0));
            assertThat(repositories).allMatch(
                    Files::isDirectory);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void overrideKeepsBuiltInArtifactPluginAndBlocksOldCapability() {
        final MavenDependencyPluginRuntime runtime =
                new MavenDependencyPluginRuntimeManager()
                        .prepare(temporary.resolve("override"),
                                List.of("-Pdev"), "3.5.0");

        assertThat(runtime.isEmbedded()).isTrue();
        assertThat(runtime.getMavenArguments())
                .contains("-Pdev", "-gs");
        assertThat(runtime.getGoal()).endsWith(
                ":3.5.0:tree");
        assertThatThrownBy(() ->
                new MavenDependencyPluginRuntimeManager()
                        .prepare(temporary.resolve("blocked"),
                                List.of(), "2.8"))
                .isInstanceOf(MavenRuntimeException.class)
                .hasMessageContaining(
                        "does not provide complete");
        assertThat(MavenDependencyPluginRuntimeManager
                .supportsCompleteEvidence("3.6.1:help"))
                .isFalse();
    }

    @Test
    void executesFromEmbeddedRepositoryWithExternalMirrorBlocked()
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
        final MavenRuntimeDescriptor maven =
                new MavenRuntimeManager().prepare(
                        null, config, null);
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager()
                        .prepare(config,
                                List.of("-gs", global.toString(),
                                        "-Dmaven.repo.local="
                                                + temporary.resolve(
                                                "empty-local")),
                                null);
        final List<String> arguments = new ArrayList<>(
                plugin.getMavenArguments());
        arguments.add("-X");
        arguments.add("-B");
        arguments.add("-f");
        arguments.add(project.resolve("pom.xml").toString());
        arguments.add(plugin.getGoal());
        arguments.add("-DoutputFile=tree.graphml");
        arguments.add("-DoutputType=graphml");
        arguments.add(plugin.getArtifactPathGoal());
        arguments.add("-Dcia.dependencyGraphFileName=tree.graphml");
        arguments.add("-Dcia.resolvedArtifactsFileName=artifacts.json");

        final MavenExecutionResult result =
                new MavenExecutor().execute(
                        maven, project, arguments);

        assertThat(result.getExitCode())
                .describedAs(result.getCombinedOutput())
                .isZero();
        assertThat(result.getCombinedOutput())
                .contains("maven-dependency-plugin:3.6.1:tree")
                .contains("(f) dependencyGraphFileName = tree.graphml")
                .contains("Artifact Path Plugin implementation=graphml-v2")
                .contains("version=" + ARTIFACT_PATH_PLUGIN_VERSION)
                .contains("sha512="
                        + plugin.getArtifactPathJarSha512());
        assertThat(project.resolve("tree.graphml"))
                .isRegularFile();
        assertThat(project.resolve("artifacts.json"))
                .content().contains("\"schemaVersion\" : 2")
                .contains("\"artifacts\" : [ ]")
                .doesNotContain("\"module\"")
                .doesNotContain("\"scope\"");
    }

    private Path settings(
            final MavenDependencyPluginRuntime runtime) {
        final List<String> arguments =
                runtime.getMavenArguments();
        final int option = arguments.indexOf("-gs");
        assertThat(option).isNotNegative();
        return Path.of(arguments.get(option + 1));
    }
}
