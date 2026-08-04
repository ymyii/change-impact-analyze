package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.util
        .CommandResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Git file set and reactor ownership tests. */
class ReactorInventoryBuilderTest {

    /** Temporary repository. */
    @TempDir
    private Path repository;

    @Test
    void discoversTrackedUntrackedAndProfileModules()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("pom.xml"), "root", """
                <modules><module>module-a</module></modules>
                <profiles><profile><id>extra</id><modules>
                <module>module-profile</module>
                </modules></profile></profiles>
                """);
        writePom(Path.of("module-a/pom.xml"),
                "module-a", "");
        writePom(Path.of("module-profile/pom.xml"),
                "module-profile", "");
        writePom(Path.of("nested/pom.xml"),
                "nested", "");
        git("add", "pom.xml", "module-a/pom.xml",
                "module-profile/pom.xml");
        git("commit", "-m", "initial");
        final RepositorySnapshot snapshot =
                new GitSnapshotProvider().open(
                        repository, null);
        try {
            final RepositoryInventory inactive =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());
            final RepositoryInventory active =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of("-Pextra"));

            assertThat(inactive.getReactors())
                    .extracting(item -> item.getRootPom()
                            .toString())
                    .containsExactly("nested/pom.xml",
                            "pom.xml");
            assertThat(inactive.getReactors().get(1)
                    .getActivePoms())
                    .doesNotContain(Path.of(
                            "module-profile/pom.xml"));
            assertThat(active.getReactors().get(1)
                    .getActivePoms())
                    .contains(Path.of(
                            "module-profile/pom.xml"));
        } finally {
            snapshot.close();
        }
    }

    @Test
    void ignoredPomIsExcluded()
            throws Exception {
        git("init");
        Files.writeString(repository.resolve(
                ".gitignore"), "ignored/\n");
        writePom(Path.of("pom.xml"), "root", "");
        writePom(Path.of("ignored/pom.xml"),
                "ignored", "");
        git("add", ".");
        git("commit", "-m", "initial");
        final RepositorySnapshot snapshot =
                new GitSnapshotProvider().open(
                        repository, null);
        try {
            assertThat(new ReactorInventoryBuilder()
                    .build(snapshot, List.of())
                    .getReactors()).hasSize(1);
        } finally {
            snapshot.close();
        }
    }

    @Test
    void isolatesMalformedPomToItsReactor()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("pom.xml"), "valid", "");
        final Path malformed = repository.resolve(
                "broken/pom.xml");
        Files.createDirectories(malformed.getParent());
        Files.writeString(malformed, "<project>");
        git("add", ".");
        git("commit", "-m", "initial");
        final RepositorySnapshot snapshot =
                new GitSnapshotProvider().open(
                        repository, null);
        try {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());

            assertThat(inventory.getReactors())
                    .hasSize(2);
            assertThat(inventory.getReactors())
                    .filteredOn(item -> item.getRootPom()
                            .equals(Path.of(
                                    "broken/pom.xml")))
                    .singleElement()
                    .satisfies(item -> assertThat(
                            item.getViolations())
                            .anyMatch(value -> value.contains(
                                    "Malformed POM")));
        } finally {
            snapshot.close();
        }
    }

    @Test
    void keepsFullReactorButSelectsOnlyPomsUnderPath()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("pom.xml"), "root", """
                <packaging>pom</packaging><modules>
                  <module>module-a</module>
                  <module>module-b</module>
                </modules>
                """);
        writePom(Path.of("module-a/pom.xml"),
                "module-a", "");
        writePom(Path.of("module-b/pom.xml"),
                "module-b", "");
        writePom(Path.of("independent/pom.xml"),
                "independent", "");
        git("add", ".");
        git("commit", "-m", "initial");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("module-b"),
                             null)) {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of());

            assertThat(snapshot.getAnalysisPath())
                    .isEqualTo(Path.of("module-b"));
            assertThat(snapshot.getAnalysisRoot())
                    .isEqualTo(repository.resolve(
                            "module-b").toRealPath());
            assertThat(inventory.getAnalysisPath())
                    .isEqualTo(Path.of("module-b"));
            assertThat(inventory.getReactors())
                    .singleElement()
                    .satisfies(reactor -> {
                        assertThat(reactor.getRootPom())
                                .isEqualTo(Path.of(
                                        "pom.xml"));
                        assertThat(reactor.getActivePoms())
                                .containsExactly(
                                        Path.of(
                                                "module-a/pom.xml"),
                                        Path.of(
                                                "module-b/pom.xml"),
                                        Path.of("pom.xml"));
                        assertThat(reactor
                                .getRequestedPoms())
                                .containsExactly(Path.of(
                                        "module-b/pom.xml"));
                        assertThat(reactor.isRootSelected())
                                .isFalse();
                    });
        }
    }

    @Test
    void returnsNoReactorWhenPathContainsNoActivePom()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("pom.xml"), "root", "");
        final Path emptyDirectory = repository.resolve(
                "documentation");
        Files.createDirectories(emptyDirectory);
        Files.writeString(emptyDirectory.resolve(
                "readme.txt"), "no pom");
        git("add", ".");
        git("commit", "-m", "initial");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             emptyDirectory, null)) {
            assertThat(new ReactorInventoryBuilder()
                    .build(snapshot, List.of())
                    .getReactors()).isEmpty();
        }
    }

    @Test
    void marksRootSelectedWithoutExpandingRequestedPoms()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("applications/app/pom.xml"),
                "app", """
                <packaging>pom</packaging><modules>
                  <module>local</module>
                  <module>../../shared</module>
                </modules>
                """);
        writePom(Path.of(
                        "applications/app/local/pom.xml"),
                "local", "");
        writePom(Path.of("shared/pom.xml"),
                "shared", "");
        git("add", ".");
        git("commit", "-m", "initial");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve(
                                     "applications/app"),
                             null)) {
            assertThat(new ReactorInventoryBuilder()
                    .build(snapshot, List.of())
                    .getReactors()).singleElement()
                    .satisfies(reactor -> {
                        assertThat(reactor.isRootSelected())
                                .isTrue();
                        assertThat(reactor.getActivePoms())
                                .containsExactly(
                                        Path.of("applications/app/"
                                                + "local/pom.xml"),
                                        Path.of("applications/app/"
                                                + "pom.xml"),
                                        Path.of(
                                                "shared/pom.xml"));
                        assertThat(reactor
                                .getRequestedPoms())
                                .containsExactly(
                                        Path.of("applications/app/"
                                                + "local/pom.xml"),
                                        Path.of("applications/app/"
                                                + "pom.xml"));
                    });
        }
    }

    @Test
    void selectsMultipleIndependentRootsUnderPath()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("services/alpha/pom.xml"),
                "alpha", "");
        writePom(Path.of("services/beta/pom.xml"),
                "beta", "");
        writePom(Path.of("outside/pom.xml"),
                "outside", "");
        git("add", ".");
        git("commit", "-m", "initial");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("services"),
                             null)) {
            final List<ReactorDescriptor> reactors =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of())
                            .getReactors();

            assertThat(reactors)
                    .extracting(ReactorDescriptor::getId)
                    .containsExactly(
                            "services/alpha/pom.xml",
                            "services/beta/pom.xml");
            assertThat(reactors)
                    .allSatisfy(reactor -> {
                        assertThat(reactor.isRootSelected())
                                .isTrue();
                        assertThat(reactor
                                .getRequestedPoms())
                                .containsExactly(
                                        reactor.getRootPom());
                    });
        }
    }

    @Test
    void distinguishesOwnedChildFromNestedReactorRoot()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("workspace/pom.xml"),
                "workspace", """
                <packaging>pom</packaging><modules>
                  <module>module</module>
                </modules>
                """);
        writePom(Path.of("workspace/module/pom.xml"),
                "module", "");
        writePom(Path.of("workspace/tools/pom.xml"),
                "tools", "");
        git("add", ".");
        git("commit", "-m", "initial");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("workspace"),
                             null)) {
            final List<ReactorDescriptor> reactors =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of())
                            .getReactors();

            assertThat(reactors)
                    .extracting(ReactorDescriptor::getId)
                    .containsExactly("workspace/pom.xml",
                            "workspace/tools/pom.xml");
            assertThat(reactors)
                    .allMatch(ReactorDescriptor
                            ::isRootSelected);
            assertThat(reactors.get(0)
                    .getRequestedPoms())
                    .containsExactly(Path.of(
                                    "workspace/module/pom.xml"),
                            Path.of("workspace/pom.xml"));
            assertThat(reactors.get(1)
                    .getRequestedPoms())
                    .containsExactly(Path.of(
                            "workspace/tools/pom.xml"));
        }
    }

    @Test
    void usesPomLocalActivationAndNullDelimitedGitPaths()
            throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        writePom(Path.of("pom.xml"), "root", """
                <profiles>
                  <profile><id>default-root</id>
                    <activation><activeByDefault>true</activeByDefault>
                    </activation><modules><module>default-module</module>
                    </modules></profile>
                </profiles>
                """);
        writePom(Path.of("activation/pom.xml"),
                "activation", """
                <profiles>
                  <profile><id>not-ci</id><activation><property>
                    <name>!ci</name></property></activation>
                    <modules><module>negated-module</module></modules>
                  </profile>
                  <profile><id>file-profile</id><activation><file>
                    <exists>.activate</exists></file></activation>
                    <modules><module>file-module</module></modules>
                  </profile>
                </profiles>
                """);
        writePom(Path.of("default-module/pom.xml"),
                "default-module", "");
        writePom(Path.of(
                        "activation/negated-module/pom.xml"),
                "negated-module", "");
        writePom(Path.of(
                        "activation/file-module/pom.xml"),
                "file-module", "");
        writePom(Path.of("odd\nname/pom.xml"),
                "odd", "");
        Files.writeString(repository.resolve(
                "activation/.activate"), "on");
        git("add", ".");
        git("commit", "-m", "initial");
        final RepositorySnapshot snapshot =
                new GitSnapshotProvider().open(
                        repository, null);
        try {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot, List.of("-Punrelated"));
            final ReactorDescriptor root = inventory
                    .getReactors().stream()
                    .filter(item -> item.getRootPom()
                            .equals(Path.of("pom.xml")))
                    .findFirst().orElseThrow();
            final ReactorDescriptor activation = inventory
                    .getReactors().stream()
                    .filter(item -> item.getRootPom()
                            .equals(Path.of(
                                    "activation/pom.xml")))
                    .findFirst().orElseThrow();

            assertThat(root.getActivePoms())
                    .contains(Path.of(
                            "default-module/pom.xml"));
            assertThat(activation.getActivePoms())
                    .contains(Path.of("activation/"
                                    + "negated-module/pom.xml"),
                            Path.of("activation/"
                                    + "file-module/pom.xml"));
            assertThat(inventory.getReactors())
                    .extracting(item -> item.getRootPom()
                            .toString())
                    .contains("odd\nname/pom.xml");
        } finally {
            snapshot.close();
        }
    }

    private void writePom(
            final Path relative,
            final String artifact,
            final String body)
            throws Exception {
        final Path file = repository.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>%s</artifactId>
                  <version>1</version>
                  %s
                </project>
                """.formatted(artifact, body));
    }

    private void git(final String... arguments)
            throws Exception {
        final List<String> command =
                new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process =
                new ProcessBuilder(
                        CommandResolver.resolve(command))
                        .directory(repository.toFile())
                        .redirectErrorStream(true).start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets
                        .UTF_8);
        assertThat(process.waitFor())
                .as(output).isZero();
    }
}
