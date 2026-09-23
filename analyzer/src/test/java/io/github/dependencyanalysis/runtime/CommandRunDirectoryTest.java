package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(run.getRunId())
                    .matches("[0-9a-f]{12}");
            workspace = run.getWorkspaceDirectory();
            temporary = run.getTemporaryDirectory();
            assertThat(workspace.getParent())
                    .isEqualTo(config.resolve(
                            "impact/ws"));
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
        final String staleId = "000000000001";
        final Path workspace = config.resolve(
                "impact/ws").resolve(staleId);
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
                "tree/ws/foreign");
        Files.createDirectories(foreign);
        Files.writeString(foreign.resolve(".owner"), "someone-else");

        try (CommandRunDirectory ignored =
                     new CommandRunDirectory(config, "tree")) {
            assertThat(foreign).isDirectory();
        }
    }

    @Test
    void reportCachesAreRunScopedAtomicAndCleaned() throws Exception {
        final Path firstRoot;
        final Path secondRoot;
        try (CommandRunDirectory first =
                     new CommandRunDirectory(config, "impact");
             CommandRunDirectory second =
                     new CommandRunDirectory(config, "impact")) {
            try (ReportCache firstCache =
                         new ReportCache(first, "impact");
                 ReportCache secondCache =
                         new ReportCache(second, "impact")) {
                firstRoot = firstCache.root();
                secondRoot = secondCache.root();
                assertThat(firstRoot).isNotEqualTo(secondRoot);
                final ReportCache.Fragment fragment =
                        firstCache.writeJsonLines("module-summary",
                                "module-a", List.of(json -> {
                                    try {
                                        json.writeStartObject();
                                        json.writeStringField("module", "a");
                                        json.writeEndObject();
                                    } catch (java.io.IOException exception) {
                                        throw new java.io.UncheckedIOException(
                                                exception);
                                    }
                                }));
                assertThat(fragment.path()).isRegularFile();
                assertThat(fragment.completeMarker()).isRegularFile();
                firstCache.complete();
                assertThat(firstRoot.resolve("manifest.json"))
                        .content().contains("\"complete\" : true")
                        .contains("module-summary");
            }
            assertThat(firstRoot).doesNotExist();
            assertThat(secondRoot).doesNotExist();
        }
    }

    @Test
    void reportCacheRejectsDuplicateRootAndCleansFailurePath() {
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "tree");
             ReportCache cache = new ReportCache(run, "tree")) {
            assertThatThrownBy(() -> new ReportCache(run, "tree"))
                    .isInstanceOf(MavenRuntimeException.class)
                    .hasMessageContaining("prepare report cache");
            assertThat(cache.root()).isDirectory();
        }
    }

    @Test
    void reportCacheRejectsIncompleteCorruptAndWrongSchemaFragments()
            throws Exception {
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache cache = new ReportCache(run, "impact")) {
            final ReportCache.Fragment fragment = cache.writeJsonLines(
                    "module-summary", "module-a", json -> {
                        json.writeStartObject();
                        json.writeBooleanField("complete", true);
                        json.writeEndObject();
                        return 1;
                    });

            Files.writeString(fragment.completeMarker(), "schema=999\n");
            assertThatThrownBy(cache::complete)
                    .isInstanceOf(MavenRuntimeException.class)
                    .hasMessageContaining("Invalid report cache fragment");
            Files.writeString(fragment.completeMarker(), "schema=1\n");

            Files.writeString(fragment.path(), "{broken");
            assertThatThrownBy(cache::complete)
                    .isInstanceOf(MavenRuntimeException.class)
                    .hasMessageContaining("Invalid report cache fragment");
            Files.writeString(fragment.path(), "{\"complete\":true}");

            final Path manifest = cache.root().resolve("manifest.json");
            Files.writeString(manifest,
                    Files.readString(manifest).replace(
                            "\"schemaVersion\" : 1",
                            "\"schemaVersion\" : 999"));
            assertThatThrownBy(cache::complete)
                    .isInstanceOf(MavenRuntimeException.class)
                    .hasMessageContaining("complete report cache manifest");
        }
    }

    @Test
    void reportCacheRefusesSymlinkCleanupAndAllowsRetry()
            throws Exception {
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "tree")) {
            final ReportCache cache = new ReportCache(run, "tree");
            final Path outside = config.resolve("outside.txt");
            Files.writeString(outside, "keep");
            final Path link = cache.root().resolve("unsafe-link");
            Files.createSymbolicLink(link, outside);

            assertThatThrownBy(cache::close)
                    .isInstanceOf(MavenRuntimeException.class)
                    .hasMessageContaining("clean report cache");
            assertThat(outside).hasContent("keep");

            Files.delete(link);
            cache.close();
            assertThat(cache.root()).doesNotExist();
            assertThat(outside).hasContent("keep");
        }
    }
}
