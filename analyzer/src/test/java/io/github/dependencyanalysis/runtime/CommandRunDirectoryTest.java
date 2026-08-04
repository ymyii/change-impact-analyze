package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests isolated config command run storage. */
class CommandRunDirectoryTest {

    /** Config directory. */
    @TempDir
    private Path config;

    @Test
    void impactRunUsesTerminalLayoutAndCleansOnlyItself()
            throws Exception {
        final Path unrelated = config.resolve("keep.txt");
        Files.writeString(unrelated, "keep");
        final Path workspace;
        final Path temporary;
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact")) {
            workspace = run.getWorkspaceDirectory();
            temporary = run.getTemporaryDirectory();
            assertThat(workspace.getParent())
                    .isEqualTo(config.resolve(
                            "impact/workspaces"));
            assertThat(temporary.getParent())
                    .isEqualTo(config.resolve("impact/tmp"));
            assertThat(config.resolve("runtime"))
                    .doesNotExist();
            assertThat(config.resolve("locks"))
                    .isDirectory();
        }
        assertThat(workspace).doesNotExist();
        assertThat(temporary).doesNotExist();
        assertThat(unrelated).hasContent("keep");
    }

    @Test
    void concurrentRunsDoNotDeleteEachOther() {
        try (CommandRunDirectory first =
                     new CommandRunDirectory(config, "tree");
             CommandRunDirectory second =
                     new CommandRunDirectory(config, "tree")) {
            assertThat(first.getWorkspaceDirectory())
                    .exists();
            assertThat(second.getWorkspaceDirectory())
                    .exists()
                    .isNotEqualTo(first
                            .getWorkspaceDirectory());
        }
    }

    @Test
    void recoversOwnedUnlockedStaleRun() throws Exception {
        final String staleId = "00000000-0000-0000-0000-000000000001";
        final Path workspace = config.resolve(
                "impact/workspaces").resolve(staleId);
        final Path temporary = config.resolve(
                "impact/tmp").resolve(staleId);
        Files.createDirectories(workspace);
        Files.createDirectories(temporary);
        final String owner = "dependency-analyzer:impact:" + staleId;
        Files.writeString(workspace.resolve(".owner"), owner);
        Files.writeString(temporary.resolve(".owner"), owner);

        try (CommandRunDirectory ignored =
                     new CommandRunDirectory(config, "impact")) {
            assertThat(workspace).doesNotExist();
            assertThat(temporary).doesNotExist();
        }
    }

    @Test
    void preservesDirectoryWithoutValidOwnerMarker() throws Exception {
        final Path foreign = config.resolve(
                "tree/workspaces/foreign");
        Files.createDirectories(foreign);
        Files.writeString(foreign.resolve(".owner"), "someone-else");

        try (CommandRunDirectory ignored =
                     new CommandRunDirectory(config, "tree")) {
            assertThat(foreign).isDirectory();
        }
    }
}
