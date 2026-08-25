package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorInventoryBuilder;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
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
                            snapshot.getRoot(),
                            snapshot.getAnalysisPath(), List.of());
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
    void buildsUpstreamClosureButReportsOnlyRequestedModule()
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
                            snapshot.getRoot(),
                            snapshot.getAnalysisPath(), List.of());
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
                    .hasSize(1)
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("module-b/pom.xml"));
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
                            snapshot.getRoot(),
                            snapshot.getAnalysisPath(), List.of());

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
            assertThat(result.getModules().get(0).getRole())
                    .isEqualTo(ModuleAnalysisRole.REQUESTED);
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
                  <properties>
                    <maven.compiler.source>8</maven.compiler.source>
                    <maven.compiler.target>8</maven.compiler.target>
                  </properties>
                  <modules><module>support</module>
                    <module>app</module></modules>
                </project>
                """);
        write(repository.resolve("support/pom.xml"),
                modulePom("support", ""));
        write(repository.resolve("app/pom.xml"),
                modulePom("app", dependency("support", "test")));
        write(repository.resolve(
                        "support/src/main/java/sample/Duplicate.java"),
                """
                        package sample;
                        public class Duplicate {
                            public int value() { return 1; }
                        }
                        """);
        write(repository.resolve(
                        "app/src/main/java/sample/Duplicate.java"),
                """
                        package sample;
                        public class Duplicate {
                            public int value() { return 2; }
                        }
                        """);
        initialize(repository);
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("scope-config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("app"), null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot.getRoot(),
                            snapshot.getAnalysisPath(), List.of());

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("compile"), "3.6.1");
            final ReactorTreeResult explicitTest =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("test"), "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("app/pom.xml"));
            assertThat(result.getModules()).flatExtracting(
                            ModuleTreeResult::getOccurrences)
                    .noneSatisfy(item -> assertThat(item.getKey()
                            .getArtifactId()).isEqualTo("support"));
            assertThat(result.getModules()).flatExtracting(
                            ModuleTreeResult::getClassConflicts)
                    .isEmpty();
            assertThat(explicitTest.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(explicitTest.getModules()).flatExtracting(
                            ModuleTreeResult::getOccurrences)
                    .singleElement().satisfies(item -> {
                        assertThat(item.getKey().getArtifactId())
                                .isEqualTo("support");
                        assertThat(item.getEffectiveScope())
                                .isEqualTo("test");
                    });
            assertThat(explicitTest.getModules()).flatExtracting(
                            ModuleTreeResult::getClassConflicts)
                    .singleElement().satisfies(conflict -> {
                        assertThat(conflict.binaryName())
                                .isEqualTo("sample/Duplicate");
                        assertThat(conflict.candidates())
                                .extracting(TreeClassConflictCandidate::scope)
                                .containsExactly("", "test");
                    });
        }
    }

    @Test
    void analyzesStandaloneProjectFromItsOwnPom()
            throws Exception {
        final Path repository = temporary.resolve(
                "standalone-repository");
        Files.createDirectories(repository);
        write(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>standalone</artifactId>
                  <version>1</version>
                </project>
                """);
        initialize(repository);
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(null,
                        temporary.resolve("standalone-config"), null);
        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository, null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot.getRoot(),
                            snapshot.getAnalysisPath(), List.of());

            final ReactorTreeResult result =
                    new TreeDependencyCollector().collect(
                            snapshot, inventory,
                            inventory.getReactors().get(0), runtime,
                            List.of(), Set.of("compile"), "3.6.1");

            assertThat(result.getStatus()).isEqualTo(
                    ReactorStatus.SUCCESS);
            assertThat(result.getModules())
                    .extracting(ModuleTreeResult::getPom)
                    .containsExactly(Path.of("pom.xml"));
            assertThat(result.getModules().get(0).getRole())
                    .isEqualTo(ModuleAnalysisRole.REQUESTED);
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
