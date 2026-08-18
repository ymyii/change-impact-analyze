package io.github.dependencyanalysis.reactor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bounded entry-POM and ancestor-aggregator scope tests. */
class ReactorInventoryBuilderTest {

    /** Temporary Git checkout. */
    @TempDir
    private Path repository;

    @BeforeEach
    void initializeGitRepository() throws Exception {
        git("init", "-q");
    }

    @Test
    void entryAggregatorDoesNotExpandToOuterReactor() throws Exception {
        writePom("pom.xml", "outer", "pom",
                "<modules><module>application</module>"
                        + "<module>unrelated</module></modules>");
        writePom("application/pom.xml", "application", "pom",
                "<modules><module>child</module></modules>");
        writePom("application/child/pom.xml", "child", "jar", "");
        writePom("unrelated/pom.xml", "unrelated", "jar", "");

        final ReactorDescriptor scope = resolve("application", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.FULL_REACTOR);
        assertThat(scope.getRootPom())
                .isEqualTo(Path.of("application/pom.xml"));
        assertThat(scope.getActivePoms()).containsExactly(
                Path.of("application/child/pom.xml"),
                Path.of("application/pom.xml"));
    }

    @Test
    void leafUsesOutermostOwningAncestor() throws Exception {
        writePom("pom.xml", "outer", "pom",
                "<modules><module>group</module>"
                        + "<module>shared</module></modules>");
        writePom("group/pom.xml", "group", "pom",
                "<modules><module>leaf</module></modules>");
        writePom("group/leaf/pom.xml", "leaf", "jar", "");
        writePom("shared/pom.xml", "shared", "jar", "");

        final ReactorDescriptor scope = resolve("group/leaf", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.SINGLE_MODULE);
        assertThat(scope.getRootPom()).isEqualTo(Path.of("pom.xml"));
        assertThat(scope.getRequestedPoms())
                .containsExactly(Path.of("group/leaf/pom.xml"));
        assertThat(scope.getActivePoms()).containsExactly(
                Path.of("group/leaf/pom.xml"),
                Path.of("group/pom.xml"), Path.of("pom.xml"),
                Path.of("shared/pom.xml"));
    }

    @Test
    void unrelatedMalformedPomIsNeverParsed() throws Exception {
        writePom("service/pom.xml", "service", "jar", "");
        final Path broken = repository.resolve("unrelated/pom.xml");
        Files.createDirectories(broken.getParent());
        Files.writeString(broken, "<project>");

        final ReactorDescriptor scope = resolve("service", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.STANDALONE);
        assertThat(scope.getActivePoms())
                .containsExactly(Path.of("service/pom.xml"));
    }

    @Test
    void nonAncestorAggregatorLeavesEntryStandalone() throws Exception {
        writePom("applications/pom.xml", "applications", "pom",
                "<modules><module>../shared</module></modules>");
        writePom("shared/pom.xml", "shared", "jar", "");

        final ReactorDescriptor scope = resolve("shared", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.STANDALONE);
        assertThat(scope.getRootPom())
                .isEqualTo(Path.of("shared/pom.xml"));
    }

    @Test
    void profileModulesUseExplicitDefaultJdkAndSettingsActivation()
            throws Exception {
        writePom("pom.xml", "root", "pom", """
                <profiles>
                  <profile><id>explicit</id><modules>
                    <module>explicit</module></modules></profile>
                  <profile><id>default</id><activation>
                    <activeByDefault>true</activeByDefault></activation>
                    <modules><module>default</module></modules></profile>
                  <profile><id>modern</id><activation><jdk>[17,)</jdk>
                    </activation><modules><module>modern</module>
                  </modules></profile>
                  <profile><id>configured-os</id><activation><os>
                    <name>Maven Test OS</name><arch>test-arch</arch>
                  </os></activation><modules><module>configured-os</module>
                  </modules></profile>
                  <profile><id>settings</id><modules>
                    <module>settings</module></modules></profile>
                  <profile><id>disabled</id><modules>
                    <module>disabled</module></modules></profile>
                </profiles>
                """);
        for (String module : List.of(
                "explicit", "default", "modern", "settings",
                "disabled", "configured-os")) {
            writePom(module + "/pom.xml", module, "jar", "");
        }
        final Path settings = repository.resolve("settings.xml");
        Files.writeString(settings, """
                <settings><activeProfiles>
                  <activeProfile>settings</activeProfile>
                  <activeProfile>disabled</activeProfile>
                </activeProfiles></settings>
                """);
        final List<String> arguments = List.of(
                "-Pexplicit,!disabled", "-s", settings.toString());
        final MavenActivationContext context =
                MavenActivationContext.resolveFromMavenOutput(
                        arguments, "Apache Maven 3.9.9\n"
                                + "Java version: 17.0.1, vendor: Test\n"
                                + "OS name: \"maven test os\", "
                                + "version: \"1\", arch: \"test-arch\", "
                                + "family: \"test\"\n", null);

        final RepositoryInventory inventory = new ReactorInventoryBuilder()
                .build(repository, Path.of(""), context);

        assertThat(inventory.getReactors().get(0).getActivePoms())
                .contains(Path.of("explicit/pom.xml"),
                        Path.of("configured-os/pom.xml"),
                        Path.of("modern/pom.xml"),
                        Path.of("settings/pom.xml"))
                .doesNotContain(Path.of("default/pom.xml"),
                        Path.of("disabled/pom.xml"));
    }

    @Test
    void inactiveProfileModuleIsNotDiscovered() throws Exception {
        writePom("pom.xml", "root", "pom", """
                <profiles><profile><id>inactive</id><modules>
                  <module>inactive</module>
                </modules></profile></profiles>
                """);
        writePom("inactive/pom.xml", "inactive", "jar", "");

        final ReactorDescriptor scope = resolve("", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.STANDALONE);
        assertThat(scope.getActivePoms())
                .containsExactly(Path.of("pom.xml"));
    }

    @Test
    void activeByDefaultModuleIsDiscovered() throws Exception {
        writePom("pom.xml", "root", "pom", """
                <profiles><profile><id>default</id><activation>
                  <activeByDefault>true</activeByDefault>
                </activation><modules><module>default</module></modules>
                </profile></profiles>
                """);
        writePom("default/pom.xml", "default", "jar", "");

        final ReactorDescriptor scope = resolve("", List.of());

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.FULL_REACTOR);
        assertThat(scope.getActivePoms())
                .contains(Path.of("default/pom.xml"));
    }

    @Test
    void selectedAggregatorFailsForMissingModuleAndCycle() throws Exception {
        writePom("pom.xml", "root", "pom",
                "<modules><module>missing</module></modules>");
        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining("Active Maven POM is unavailable")
                .hasMessageContaining("missing/pom.xml");

        writePom("pom.xml", "root", "pom",
                "<modules><module>child</module></modules>");
        writePom("child/pom.xml", "child", "pom",
                "<modules><module>..</module></modules>");
        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining("module cycle");
    }

    @Test
    void rejectsMissingEntryPomAndModuleOutsideRepository() throws Exception {
        assertThatThrownBy(() -> resolve("missing", List.of()))
                .hasMessageContaining("Active Maven POM is unavailable")
                .hasMessageContaining("missing/pom.xml");

        writePom("pom.xml", "root", "pom",
                "<modules><module>../outside</module></modules>");
        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining("module leaves Git root");
    }

    @Test
    void rejectsDuplicateModuleCoordinates() throws Exception {
        writePom("pom.xml", "root", "pom",
                "<modules><module>first</module>"
                        + "<module>second</module></modules>");
        writePom("first/pom.xml", "duplicate", "jar", "");
        writePom("second/pom.xml", "duplicate", "jar", "");

        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining(
                        "Duplicate version-independent module coordinate")
                .hasMessageContaining("example:duplicate");
    }

    @Test
    void rejectsDuplicateModulePaths() throws Exception {
        writePom("pom.xml", "root", "pom",
                "<modules><module>child</module>"
                        + "<module>child/pom.xml</module></modules>");
        writePom("child/pom.xml", "child", "jar", "");

        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining("Duplicate active Maven module path")
                .hasMessageContaining("child/pom.xml");
    }

    @Test
    void readsDefaultGlobalSettingsFromConfiguredMavenRuntime()
            throws Exception {
        writePom("pom.xml", "root", "pom", """
                <profiles><profile><id>global</id><modules>
                  <module>global</module>
                </modules></profile></profiles>
                """);
        writePom("global/pom.xml", "global", "jar", "");
        final Path executable = repository.resolve("maven/bin/mvn");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "maven");
        final Path globalSettings = repository.resolve(
                "maven/conf/settings.xml");
        Files.createDirectories(globalSettings.getParent());
        Files.writeString(globalSettings, """
                <settings><activeProfiles>
                  <activeProfile>global</activeProfile>
                </activeProfiles></settings>
                """);
        final MavenActivationContext context =
                MavenActivationContext.resolve(
                        List.of(), "17", executable);

        final ReactorDescriptor scope = new ReactorInventoryBuilder()
                .build(repository, Path.of(""), context)
                .getReactors().get(0);

        assertThat(scope.getScopeMode())
                .isEqualTo(ReactorScopeMode.FULL_REACTOR);
        assertThat(scope.getActivePoms())
                .contains(Path.of("global/pom.xml"));
    }

    @Test
    void ignoredEntryPomIsRejected() throws Exception {
        Files.writeString(repository.resolve(".gitignore"), "ignored/\n");
        writePom("ignored/pom.xml", "ignored", "jar", "");

        assertThatThrownBy(() -> resolve("ignored", List.of()))
                .hasMessageContaining("ignored by Git");
    }

    @Test
    void rejectsPomSymlinkThatEscapesRepository() throws Exception {
        final Path outside = repository.resolveSibling(
                repository.getFileName() + "-outside-pom.xml");
        Files.writeString(outside, """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>outside</artifactId>
                  <version>1</version>
                </project>
                """);
        final Path linked = repository.resolve("linked/pom.xml");
        Files.createDirectories(linked.getParent());
        Files.createSymbolicLink(linked, outside);

        try {
            assertThatThrownBy(() -> resolve("linked", List.of()))
                    .hasMessageContaining("resolves outside Git root")
                    .hasMessageContaining("linked/pom.xml");
        } finally {
            Files.deleteIfExists(linked);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void activePomInsideGitSubmoduleIsRejected() throws Exception {
        final Path source = repository.resolveSibling(
                repository.getFileName() + "-submodule-source");
        Files.createDirectories(source);
        gitAt(source, "init", "-q");
        gitAt(source, "config", "user.email", "test@example.com");
        gitAt(source, "config", "user.name", "Test");
        Files.writeString(source.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>submodule</artifactId>
                  <version>1</version>
                </project>
                """);
        gitAt(source, "add", ".");
        gitAt(source, "commit", "-q", "-m", "initial");
        git("-c", "protocol.file.allow=always", "submodule", "add",
                "-q", source.toString(), "submodule");
        writePom("pom.xml", "root", "pom",
                "<modules><module>submodule</module></modules>");

        assertThatThrownBy(() -> resolve("", List.of()))
                .hasMessageContaining("inside a Git submodule")
                .hasMessageContaining("submodule/pom.xml");
    }

    private ReactorDescriptor resolve(
            final String path,
            final List<String> arguments) throws Exception {
        return new ReactorInventoryBuilder().build(
                repository, Path.of(path), arguments)
                .getReactors().get(0);
    }

    private void writePom(
            final String path,
            final String artifact,
            final String packaging,
            final String body) throws Exception {
        final Path pom = repository.resolve(path);
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>%s</artifactId><version>1</version>
                  <packaging>%s</packaging>%s
                </project>
                """.formatted(artifact, packaging, body));
    }

    private void git(final String... arguments) throws Exception {
        gitAt(repository, arguments);
    }

    private void gitAt(
            final Path directory,
            final String... arguments) throws Exception {
        final List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true).start();
        assertThat(process.waitFor()).isZero();
    }
}
