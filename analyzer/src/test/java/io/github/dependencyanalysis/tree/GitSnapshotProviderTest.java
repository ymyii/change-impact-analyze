package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.util
        .CommandResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Git analysis path mapping tests. */
class GitSnapshotProviderTest {

    /** Temporary repository. */
    @TempDir
    private Path repository;

    @Test
    void mapsSubdirectoryIntoDetachedSnapshot()
            throws Exception {
        initialize();
        write("module/pom.xml", "<project/>");
        git("add", ".");
        git("commit", "-m", "initial");
        write("later.txt", "later");
        git("add", ".");
        git("commit", "-m", "later");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository.resolve("module"),
                             "HEAD~1")) {
            assertThat(snapshot.getAnalysisPath())
                    .isEqualTo(Path.of("module"));
            assertThat(snapshot.getAnalysisRoot())
                    .isDirectory();
            assertThat(snapshot.getAnalysisRoot()
                    .resolve("pom.xml")).exists();
            assertThat(snapshot.getRoot())
                    .isNotEqualTo(repository);
        }
    }

    @Test
    void rejectsPathMissingFromRequestedRef()
            throws Exception {
        initialize();
        write("pom.xml", "<project/>");
        git("add", ".");
        git("commit", "-m", "initial");
        write("new-module/pom.xml", "<project/>");
        git("add", ".");
        git("commit", "-m", "new module");

        assertThatThrownBy(() ->
                new GitSnapshotProvider().open(
                        repository.resolve("new-module"),
                        "HEAD~1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(
                        "does not exist at ref");
    }

    @Test
    void detachedSnapshotUsesCommandWorkspaceDirectory()
            throws Exception {
        initialize();
        write("pom.xml", "<project/>");
        git("add", ".");
        git("commit", "-m", "initial");
        final Path commandWorkspace = repository
                .getParent().resolve("config/tree/ws/run-id");

        try (RepositorySnapshot snapshot =
                     new GitSnapshotProvider().open(
                             repository, "HEAD",
                             commandWorkspace)) {
            assertThat(snapshot.getRoot())
                    .isEqualTo(commandWorkspace);
        }
        assertThat(commandWorkspace)
                .doesNotExist();
    }

    private void initialize() throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
    }

    private void write(
            final String relative,
            final String content)
            throws Exception {
        final Path file = repository.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void git(final String... arguments)
            throws Exception {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(command))
                .directory(repository.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(process
                .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }
}
