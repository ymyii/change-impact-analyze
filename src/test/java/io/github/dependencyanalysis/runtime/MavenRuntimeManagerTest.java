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

import static org.assertj.core.api.Assertions
        .assertThat;

/** Embedded Maven extraction tests. */
class MavenRuntimeManagerTest {

    /** Minimum realistic Maven script size. */
    private static final long MINIMUM_SCRIPT_SIZE = 100L;

    /** Concurrent runtime preparation count. */
    private static final int CONCURRENT_PREPARATIONS = 4;

    /** Temporary config parent. */
    @TempDir
    private Path temporary;

    @Test
    void extractsAndReusesVerifiedRuntime()
            throws Exception {
        final Path config = temporary.resolve("config");
        final MavenRuntimeManager manager =
                new MavenRuntimeManager();

        final MavenRuntimeDescriptor first =
                manager.prepare(null, config, null);
        final long modified = Files.getLastModifiedTime(
                first.getExecutable()).toMillis();
        final MavenRuntimeDescriptor second =
                manager.prepare(null, config, null);

        assertThat(first.getSource()).isEqualTo(
                MavenRuntimeSource.EMBEDDED);
        assertThat(first.getExecutable())
                .isRegularFile();
        assertThat(second.getExecutable())
                .isEqualTo(first.getExecutable());
        assertThat(Files.getLastModifiedTime(
                second.getExecutable()).toMillis())
                .isEqualTo(modified);
    }

    @Test
    void damagedRuntimeWithMarkerRebuildsAndPreservesUnknownFiles()
            throws Exception {
        final Path config = temporary.resolve("damaged");
        final MavenRuntimeManager manager =
                new MavenRuntimeManager();
        final MavenRuntimeDescriptor first =
                manager.prepare(null, config, null);
        final Path unknown = config.resolve(
                "future-user-file.txt");
        Files.writeString(unknown, "keep");
        Files.writeString(first.getExecutable(),
                "damaged");
        final Path marker = first.getExecutable()
                .getParent().getParent()
                .getParent().resolve(
                        ".dependency-analyzer-complete");
        assertThat(marker).isRegularFile();

        final MavenRuntimeDescriptor rebuilt =
                manager.prepare(null, config, null);

        assertThat(rebuilt.getExecutable())
                .isRegularFile();
        assertThat(Files.size(
                rebuilt.getExecutable()))
                .isGreaterThan(MINIMUM_SCRIPT_SIZE);
        assertThat(unknown).hasContent("keep");
    }

    @Test
    void concurrentPreparationReturnsOneCompleteRuntime()
            throws Exception {
        final Path config = temporary.resolve(
                "concurrent");
        final MavenRuntimeManager manager =
                new MavenRuntimeManager();
        final ExecutorService executor =
                Executors.newFixedThreadPool(
                        CONCURRENT_PREPARATIONS);
        try {
            final List<java.util.concurrent.Callable<
                    MavenRuntimeDescriptor>> tasks =
                    new ArrayList<>();
            for (int index = 0;
                 index < CONCURRENT_PREPARATIONS;
                 index++) {
                tasks.add(() -> manager.prepare(
                        null, config, null));
            }
            final List<Future<MavenRuntimeDescriptor>>
                    futures = executor.invokeAll(tasks);
            final List<Path> executables =
                    new ArrayList<>();
            for (Future<MavenRuntimeDescriptor> future
                    : futures) {
                executables.add(future.get()
                        .getExecutable());
            }

            assertThat(executables).allMatch(
                    Files::isRegularFile);
            assertThat(executables).containsOnly(
                    executables.get(0));
        } finally {
            executor.shutdownNow();
        }
    }
}
