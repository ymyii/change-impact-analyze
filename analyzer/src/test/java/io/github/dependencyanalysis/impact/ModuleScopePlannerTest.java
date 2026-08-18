package io.github.dependencyanalysis.impact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests reactor-root and leaf Module planning. */
class ModuleScopePlannerTest {

    /** Temporary Git reactor. */
    @TempDir
    private Path repository;

    @BeforeEach
    void createReactor() throws Exception {
        run("git", "init", "-q");
        Files.writeString(repository.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>a</module><module>b</module></modules>
                </project>
                """);
        module("a");
        module("b");
    }

    @Test
    void reactorRootSelectsAllActiveModules() throws Exception {
        final ReactorAnalysisScope scope =
                new ModuleScopePlanner().plan(repository, List.of());

        assertThat(scope.getMode()).isEqualTo(AnalysisMode.REACTOR);
        assertThat(scope.getModules()).extracting(
                value -> value.getCoordinate().getArtifactId())
                .containsExactly("a", "b");
        assertThat(scope.getProjectArguments()).isEmpty();
    }

    @Test
    void leafSelectsOnlyCurrentModuleWithUpstreamClosure() throws Exception {
        final ReactorAnalysisScope scope = new ModuleScopePlanner().plan(
                repository.resolve("a"), List.of());

        assertThat(scope.getMode())
                .isEqualTo(AnalysisMode.SINGLE_MODULE);
        assertThat(scope.getModules()).extracting(
                value -> value.getCoordinate().getArtifactId())
                .containsExactly("a");
        assertThat(scope.getAllModules()).hasSize(2);
        assertThat(scope.getProjectArguments())
                .containsExactly("-pl", "a", "-am");
    }

    @Test
    void nestedAggregatorDoesNotExpandToOuterReactor() throws Exception {
        final Path nested = repository.resolve("nested");
        Files.createDirectories(nested.resolve("child"));
        Files.writeString(nested.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>nested</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <modules><module>child</module></modules>
                </project>
                """);
        Files.writeString(nested.resolve("child/pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>nested-child</artifactId>
                  <version>1</version>
                </project>
                """);

        final ReactorAnalysisScope scope =
                new ModuleScopePlanner().plan(nested, List.of());

        assertThat(scope.getMode()).isEqualTo(AnalysisMode.REACTOR);
        assertThat(scope.getReactorRoot()).isEqualTo(nested.toRealPath());
        assertThat(scope.getModules()).extracting(
                value -> value.getCoordinate().getArtifactId())
                .containsExactly("nested-child");
        assertThat(scope.getAllModules()).hasSize(1);
        assertThat(scope.getProjectArguments()).isEmpty();
    }

    @Test
    void nonOwnedProjectUsesStandaloneScope() throws Exception {
        final Path standalone = repository.resolve("standalone");
        Files.createDirectories(standalone);
        Files.writeString(standalone.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>standalone</artifactId>
                  <version>1</version>
                </project>
                """);

        final ReactorAnalysisScope scope =
                new ModuleScopePlanner().plan(standalone, List.of());

        assertThat(scope.getMode())
                .isEqualTo(AnalysisMode.SINGLE_MODULE);
        assertThat(scope.getReactorRoot())
                .isEqualTo(standalone.toRealPath());
        assertThat(scope.getModules()).extracting(
                value -> value.getCoordinate().getArtifactId())
                .containsExactly("standalone");
        assertThat(scope.getAllModules()).hasSize(1);
        assertThat(scope.getProjectArguments()).isEmpty();
    }

    private void module(final String name) throws Exception {
        final Path directory = repository.resolve(name);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>example</groupId>
                    <artifactId>root</artifactId><version>1</version>
                  </parent>
                  <artifactId>%s</artifactId>
                </project>
                """.formatted(name));
    }

    private void run(final String... command) throws Exception {
        final Process process = new ProcessBuilder(command)
                .directory(repository.toFile()).start();
        assertThat(process.waitFor()).isZero();
    }
}
