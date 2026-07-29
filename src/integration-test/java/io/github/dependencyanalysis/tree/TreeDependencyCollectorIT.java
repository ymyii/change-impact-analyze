package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeManager;
import io.github.dependencyanalysis.util
        .CommandResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Reactor-aware dependency collection integration tests. */
class TreeDependencyCollectorIT {

    /** Temporary directory. */
    @TempDir
    private Path temporary;

    @Test
    void resolvesSiblingDependencyFromReactor()
            throws Exception {
        final Path repository = temporary.resolve("repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>module-a</module>
                    <module>module-b</module>
                    <module>module-c</module></modules>
                </project>
                """);
        write(repository.resolve("module-a/pom.xml"),
                modulePom("module-a", ""));
        write(repository.resolve("module-b/pom.xml"),
                modulePom("module-b", """
                        <dependencies><dependency>
                          <groupId>test</groupId>
                          <artifactId>module-a</artifactId>
                          <version>1</version>
                        </dependency></dependencies>
                        """));
        write(repository.resolve("module-c/pom.xml"),
                modulePom("module-c", ""));
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository, null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());
            final ReactorDescriptor reactor = inventory
                    .getReactors().get(0);

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory, reactor,
                            runtime, List.of(),
                            Set.of("compile", "runtime"),
                            "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules()).hasSize(3)
                    .allSatisfy(module -> assertThat(
                            module.getRole()).isEqualTo(
                            ModuleAnalysisRole.REACTOR_ROOT_SCOPE));
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .doesNotContain(Path.of("pom.xml"));
            assertThat(result.getModules()).flatExtracting(
                            ModuleTreeResult::getOccurrences)
                    .filteredOn(item -> item.getKey()
                            .getArtifactId().equals("module-a"))
                    .anySatisfy(item -> assertThat(
                            item.isReactorModule()).isTrue());
        }
    }

    @Test
    void buildsFullReactorAndAddsRequestedDependencies()
            throws Exception {
        final Path repository = createSiblingReactor();
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("scoped-config"),
                        null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("module-b"),
                             null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());
            final ReactorDescriptor reactor = inventory
                    .getReactors().get(0);

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory, reactor,
                            runtime, List.of(),
                            Set.of("compile", "runtime"),
                            "3.6.1");

            assertThat(reactor.getActivePoms())
                    .hasSize(4);
            assertThat(reactor.getRequestedPoms())
                    .containsExactly(Path.of(
                            "module-b/pom.xml"));
            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .hasSize(2)
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(
                            Path.of("module-b/pom.xml"),
                            Path.of("module-a/pom.xml"));
            assertThat(result.getModules())
                    .filteredOn(module -> module.getPom()
                            .equals(Path.of(
                                    "module-b/pom.xml")))
                    .singleElement()
                    .satisfies(module -> {
                        assertThat(module.getRole())
                                .isEqualTo(
                                        ModuleAnalysisRole.REQUESTED);
                        assertThat(module.getOccurrences())
                                .filteredOn(item -> item
                                        .getKey().getArtifactId()
                                        .equals("module-a"))
                                .anySatisfy(item ->
                                        assertThat(item
                                                .isReactorModule())
                                                .isTrue());
                    });
            assertThat(result.getModules())
                    .filteredOn(module -> module.getPom()
                            .equals(Path.of(
                                    "module-a/pom.xml")))
                    .singleElement()
                    .satisfies(module -> assertThat(
                            module.getRole()).isEqualTo(
                            ModuleAnalysisRole.DEPENDENCY));
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .doesNotContain(Path.of("pom.xml"));
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .doesNotContain(Path.of(
                            "module-c/pom.xml"));
        }
    }

    @Test
    void followsScopedTransitiveReactorDependencies()
            throws Exception {
        final Path repository = temporary.resolve(
                "transitive-repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>core</module><module>middle</module>
                    <module>app</module><module>unrelated</module></modules>
                </project>
                """);
        write(repository.resolve("core/pom.xml"),
                modulePom("core", ""));
        write(repository.resolve("middle/pom.xml"),
                modulePom("middle", dependency("core", "compile")));
        write(repository.resolve("app/pom.xml"),
                modulePom("app", dependency("middle", "compile")));
        write(repository.resolve("unrelated/pom.xml"),
                modulePom("unrelated", ""));
        initialize(repository);
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("transitive-config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("app"), null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("compile"), "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("app/pom.xml"),
                            Path.of("core/pom.xml"),
                            Path.of("middle/pom.xml"));
            assertThat(result.getModules().get(0).getRole())
                    .isEqualTo(ModuleAnalysisRole.REQUESTED);
            assertThat(result.getModules().subList(1, 3))
                    .allSatisfy(module -> assertThat(
                            module.getRole()).isEqualTo(
                            ModuleAnalysisRole.DEPENDENCY));
        }
    }

    @Test
    void excludesDependencyModuleOutsideScopeFilter()
            throws Exception {
        final Path repository = temporary.resolve(
                "scope-repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>support</module>
                    <module>app</module></modules>
                </project>
                """);
        write(repository.resolve("support/pom.xml"),
                modulePom("support", ""));
        write(repository.resolve("app/pom.xml"),
                modulePom("app", dependency("support", "test")));
        initialize(repository);
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("scope-config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("app"), null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("compile"), "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("app/pom.xml"));
        }
    }

    @Test
    void selectsMultipleRequestedModulesBeforeDependencies()
            throws Exception {
        final Path repository = temporary.resolve(
                "multiple-requested-repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>library</module>
                    <module>apps/one</module><module>apps/two</module>
                    <module>unrelated</module></modules>
                </project>
                """);
        write(repository.resolve("library/pom.xml"),
                modulePom("library", ""));
        write(repository.resolve("apps/one/pom.xml"),
                nestedModulePom("one",
                        dependency("library", "compile")));
        write(repository.resolve("apps/two/pom.xml"),
                nestedModulePom("two", ""));
        write(repository.resolve("unrelated/pom.xml"),
                modulePom("unrelated", ""));
        initialize(repository);
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("multiple-config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("apps"), null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("compile"), "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("apps/one/pom.xml"),
                            Path.of("apps/two/pom.xml"),
                            Path.of("library/pom.xml"));
            assertThat(result.getModules().subList(0, 2))
                    .allSatisfy(module -> assertThat(
                            module.getRole()).isEqualTo(
                            ModuleAnalysisRole.REQUESTED));
            assertThat(result.getModules().get(2).getRole())
                    .isEqualTo(ModuleAnalysisRole.DEPENDENCY);
        }
    }

    private Path createSiblingReactor()
            throws Exception {
        final Path repository = temporary.resolve(
                "scoped-repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>module-a</module>
                    <module>module-b</module>
                    <module>module-c</module></modules>
                </project>
                """);
        write(repository.resolve("module-a/pom.xml"),
                modulePom("module-a", ""));
        write(repository.resolve("module-b/pom.xml"),
                modulePom("module-b", """
                        <dependencies><dependency>
                          <groupId>test</groupId>
                          <artifactId>module-a</artifactId>
                          <version>1</version>
                        </dependency></dependencies>
                        """));
        write(repository.resolve("module-c/pom.xml"),
                modulePom("module-c", ""));
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
        return repository;
    }

    private String dependency(
            final String artifact,
            final String scope) {
        return """
                <dependencies><dependency>
                  <groupId>test</groupId><artifactId>%s</artifactId>
                  <version>1</version><scope>%s</scope>
                </dependency></dependencies>
                """.formatted(artifact, scope);
    }

    private void initialize(final Path repository)
            throws Exception {
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
    }

    private String modulePom(
            final String artifact,
            final String body) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>test</groupId>
                    <artifactId>root</artifactId><version>1</version>
                    <relativePath>../pom.xml</relativePath></parent>
                  <artifactId>%s</artifactId>%s
                </project>
                """.formatted(artifact, body);
    }

    private String nestedModulePom(
            final String artifact,
            final String body) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>test</groupId>
                    <artifactId>root</artifactId><version>1</version>
                    <relativePath>../../pom.xml</relativePath></parent>
                  <artifactId>%s</artifactId>%s
                </project>
                """.formatted(artifact, body);
    }

    private void write(
            final Path path,
            final String content)
            throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void git(
            final Path repository,
            final String... arguments)
            throws Exception {
        final List<String> command =
                new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(command))
                .directory(repository.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(process
                .getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }
}
