package io.github.dependencyanalysis.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Wiki: wiki/features/maven-runtime.md - 内嵌 Maven runtime 准备入口
/** Selects and safely prepares Apache Maven 3.6.3. */
public final class MavenRuntimeManager {

    /** JVM-local guards supplementing cross-process file locks. */
    private static final ConcurrentMap<Path, Object> JVM_LOCKS =
            new ConcurrentHashMap<>();

    /** Embedded Maven version. */
    public static final String EMBEDDED_VERSION = "3.6.3";

    /** ZIP resource path. */
    private static final String ZIP_RESOURCE =
            "/maven/apache-maven-3.6.3-bin.zip";

    /** Extracted root directory. */
    private static final String DISTRIBUTION_ROOT =
            "apache-maven-3.6.3";

    /** Completion marker name. */
    private static final String COMPLETE_MARKER =
            ".dependency-analyzer-complete";

    /** @return default complete config directory */
    public static Path defaultConfigDir() {
        return Path.of(System.getProperty("user.home"),
                ".dependency-analyzer");
    }

    /**
     * Selects a user runtime or prepares embedded Maven.
     *
     * @param configuredExecutable user executable, nullable
     * @param configuredDir config directory, nullable
     * @param javaHome Maven JAVA_HOME, nullable
     * @return runtime descriptor without version probe
     */
    public MavenRuntimeDescriptor prepare(
            final Path configuredExecutable,
            final Path configuredDir,
            final Path javaHome) {
        final Path configDir = configuredDir == null
                ? defaultConfigDir()
                : configuredDir.toAbsolutePath().normalize();
        try {
            prepareConfigDir(configDir);
            if (configuredExecutable != null) {
                return userConfigured(
                        configuredExecutable, configDir, javaHome);
            }
            return embedded(configDir, javaHome);
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to prepare Maven runtime", exception);
        }
    }

    private MavenRuntimeDescriptor userConfigured(
            final Path executable,
            final Path configDir,
            final Path javaHome) {
        final Path absolute = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new MavenRuntimeException(
                    "Maven executable is not a file: " + absolute);
        }
        if (!isWindows() && !Files.isExecutable(absolute)) {
            throw new MavenRuntimeException(
                    "Maven executable is not executable: " + absolute);
        }
        return new MavenRuntimeDescriptor(
                MavenRuntimeSource.USER_CONFIGURED,
                absolute, null, javaHome, configDir);
    }

    private MavenRuntimeDescriptor embedded(
            final Path configDir,
            final Path javaHome) throws IOException {
        final Path versionDir = configDir.resolve("runtime")
                .resolve("apache-maven").resolve(EMBEDDED_VERSION);
        final Path runtimeLeaf = versionDir.resolve("content");
        final Path lockPath = configDir.resolve("locks").resolve(
                "apache-maven-" + EMBEDDED_VERSION + ".lock");
        Files.createDirectories(lockPath.getParent());
        final Path normalizedLock = lockPath.toAbsolutePath().normalize();
        final Object jvmLock = JVM_LOCKS.computeIfAbsent(
                normalizedLock, ignored -> new Object());
        synchronized (jvmLock) {
            try (FileChannel channel = FileChannel.open(
                    lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                if (!isComplete(runtimeLeaf)) {
                    deleteManagedLeaf(runtimeLeaf, versionDir);
                    extract(runtimeLeaf, versionDir);
                }
            }
        }
        final Path executable = runtimeLeaf.resolve(DISTRIBUTION_ROOT)
                .resolve("bin").resolve(executableNameFor(
                        System.getProperty("os.name", "")));
        if (!isWindows()) {
            executable.toFile().setExecutable(true, true);
        }
        return new MavenRuntimeDescriptor(
                MavenRuntimeSource.EMBEDDED,
                executable, null, javaHome, configDir);
    }

    private void prepareConfigDir(final Path configDir) throws IOException {
        Files.createDirectories(configDir);
        if (configDir.equals(defaultConfigDir()
                .toAbsolutePath().normalize())) {
            final DosFileAttributeView view = Files.getFileAttributeView(
                    configDir, DosFileAttributeView.class);
            if (view != null) {
                try {
                    view.setHidden(true);
                } catch (UnsupportedOperationException | IOException ignored) {
                    // POSIX uses the leading dot.
                }
            }
        }
    }

    private boolean isComplete(final Path runtimeLeaf) {
        final Path distribution = runtimeLeaf.resolve(DISTRIBUTION_ROOT);
        final Path marker = runtimeLeaf.resolve(COMPLETE_MARKER);
        final Path executable = distribution.resolve("bin").resolve(
                executableNameFor(System.getProperty("os.name", "")));
        final Path settings = distribution.resolve("conf/settings.xml");
        final Path boot = distribution.resolve(
                "boot/plexus-classworlds-2.6.0.jar");
        try {
            return Files.isRegularFile(executable,
                    LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(settings,
                    LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(boot,
                    LinkOption.NOFOLLOW_LINKS)
                    && EMBEDDED_VERSION.equals(
                    Files.readString(marker).trim());
        } catch (IOException exception) {
            return false;
        }
    }

    private void extract(
            final Path runtimeLeaf,
            final Path versionDir) throws IOException {
        Files.createDirectories(versionDir);
        final Path staging = versionDir.resolve(
                ".staging-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            extractZip(staging);
            Files.writeString(staging.resolve(COMPLETE_MARKER),
                    EMBEDDED_VERSION);
            move(staging, runtimeLeaf);
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
        }
    }

    private void extractZip(final Path destination) throws IOException {
        try (InputStream raw = resource(ZIP_RESOURCE);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path output = destination.resolve(entry.getName())
                        .normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException(
                            "Unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output,
                            StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private InputStream resource(final String name) throws IOException {
        final InputStream stream = MavenRuntimeManager.class
                .getResourceAsStream(name);
        if (stream == null) {
            throw new IOException("Missing resource: " + name);
        }
        return stream;
    }

    private void move(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException
                 | DirectoryNotEmptyException exception) {
            Files.move(source, target);
        }
    }

    private void deleteManagedLeaf(
            final Path runtimeLeaf,
            final Path versionDir) throws IOException {
        final Path normalizedLeaf = runtimeLeaf.toAbsolutePath().normalize();
        final Path normalizedVersion = versionDir.toAbsolutePath().normalize();
        if (!normalizedLeaf.getParent().equals(normalizedVersion)) {
            throw new IOException("Refusing unmanaged runtime cleanup");
        }
        if (Files.exists(normalizedLeaf)) {
            deleteTree(normalizedLeaf);
        }
    }

    private void deleteTree(final Path root) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            final Path[] paths = stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean isWindows() {
        return executableNameFor(System.getProperty("os.name", ""))
                .equals("mvn.cmd");
    }

    /**
     * Selects the Maven launcher for an operating system.
     *
     * @param osName operating system name
     * @return mvn.cmd on Windows, mvn otherwise
     */
    static String executableNameFor(final String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("win")
                ? "mvn.cmd" : "mvn";
    }
}
