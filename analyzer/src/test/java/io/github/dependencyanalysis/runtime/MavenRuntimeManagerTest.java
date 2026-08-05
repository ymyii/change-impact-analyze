package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    void extractsAndReusesVersionedRuntime()
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
    void missingRequiredFileRebuildsAndPreservesUnknownFiles()
            throws Exception {
        final Path config = temporary.resolve("damaged");
        final MavenRuntimeManager manager =
                new MavenRuntimeManager();
        final MavenRuntimeDescriptor first =
                manager.prepare(null, config, null);
        final Path unknown = config.resolve(
                "future-user-file.txt");
        Files.writeString(unknown, "keep");
        final Path settings = first.getExecutable()
                .getParent().getParent()
                .resolve("conf/settings.xml");
        Files.delete(settings);
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
    void presentRuntimeContentIsNotFingerprinted() throws Exception {
        final Path config = temporary.resolve("content-change");
        final MavenRuntimeManager manager = new MavenRuntimeManager();
        final MavenRuntimeDescriptor first = manager.prepare(
                null, config, null);
        Files.writeString(first.getExecutable(), "damaged-but-present");

        final MavenRuntimeDescriptor reused = manager.prepare(
                null, config, null);

        assertThat(reused.getExecutable()).hasContent(
                "damaged-but-present");
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

    @Test
    void embeddedArchiveContainsPosixAndWindowsLaunchers()
            throws Exception {
        final InputStream resource =
                MavenRuntimeManager.class
                        .getResourceAsStream(
                                "/maven/apache-maven-3.6.3-bin.zip");
        assertThat(resource).isNotNull();
        final java.util.Set<String> entries =
                new HashSet<>();
        try (ZipInputStream zip =
                     new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry())
                    != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).contains(
                "apache-maven-3.6.3/bin/mvn",
                "apache-maven-3.6.3/bin/mvn.cmd",
                "apache-maven-3.6.3/bin/mvnDebug",
                "apache-maven-3.6.3/bin/mvnDebug.cmd");
    }

    @Test
    void selectsPlatformSpecificLauncher() {
        assertThat(MavenRuntimeManager
                .executableNameFor("Windows 11"))
                .isEqualTo("mvn.cmd");
        assertThat(MavenRuntimeManager
                .executableNameFor("Linux"))
                .isEqualTo("mvn");
        assertThat(MavenRuntimeManager
                .executableNameFor("Mac OS X"))
                .isEqualTo("mvn");
    }
}
