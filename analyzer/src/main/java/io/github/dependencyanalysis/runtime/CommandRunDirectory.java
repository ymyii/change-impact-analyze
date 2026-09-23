package io.github.dependencyanalysis.runtime;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.security.SecureRandom;
import java.util.stream.Stream;

/** Owns one isolated command workspace and temporary directory. */
public final class CommandRunDirectory implements AutoCloseable {

    /** Number of run identifier allocation attempts. */
    private static final int MAX_ALLOCATION_ATTEMPTS = 100;

    /** Number of random bytes in a run identifier. */
    private static final int RUN_ID_BYTES = 6;

    /** Mask for unsigned byte formatting. */
    private static final int BYTE_MASK = 0xff;

    /** Run identifier character count. */
    private static final int RUN_ID_LENGTH = 12;

    /** Run identifier source. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Owner marker filename. */
    private static final String OWNER_MARKER = ".owner";

    /** Marker prefix. */
    private static final String OWNER_PREFIX =
            "dependency-analyzer:";

    /** Command name. */
    private final String command;

    /** Run identifier. */
    private final String runId;

    /** Workspaces parent. */
    private final Path workspacesRoot;

    /** Temporary files parent. */
    private final Path temporaryRoot;

    /** Per-run workspace. */
    private final Path workspaceDirectory;

    /** Per-run temporary directory. */
    private final Path temporaryDirectory;

    /** Lock path. */
    private final Path lockPath;

    /** Lock channel. */
    private final FileChannel lockChannel;

    /** Active run lock. */
    private final FileLock lock;

    /** Closed flag. */
    private boolean closed;

    /**
     * Creates and locks a command run directory.
     *
     * @param configDirectory complete config directory
     * @param subcommand impact or tree
     */
    public CommandRunDirectory(
            final Path configDirectory,
            final String subcommand) {
        command = validateCommand(subcommand);
        final Path config = Objects.requireNonNull(
                configDirectory, "configDirectory")
                .toAbsolutePath().normalize();
        workspacesRoot = config.resolve(command)
                .resolve("ws");
        temporaryRoot = config.resolve(command)
                .resolve("tmp");
        FileChannel preparedChannel = null;
        FileLock preparedLock = null;
        Path preparedWorkspace = null;
        Path preparedTemporary = null;
        Path preparedLockPath = null;
        String preparedRunId = null;
        IOException failure = null;
        try {
            Files.createDirectories(workspacesRoot);
            Files.createDirectories(temporaryRoot);
            Files.createDirectories(config.resolve("locks"));
            recoverStaleRuns(config);
            for (int attempt = 0; attempt < MAX_ALLOCATION_ATTEMPTS;
                    attempt++) {
                final String candidate = newRunId();
                final Path candidateLock = config.resolve("locks")
                        .resolve(command + "-" + candidate + ".lock");
                try {
                    preparedChannel = FileChannel.open(candidateLock,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    preparedLock = preparedChannel.lock();
                    preparedWorkspace = workspacesRoot.resolve(candidate);
                    preparedTemporary = temporaryRoot.resolve(candidate);
                    Files.createDirectory(preparedWorkspace);
                    Files.createDirectory(preparedTemporary);
                    writeOwner(preparedWorkspace, candidate);
                    writeOwner(preparedTemporary, candidate);
                    preparedLockPath = candidateLock;
                    preparedRunId = candidate;
                    break;
                } catch (FileAlreadyExistsException collision) {
                    closeQuietly(preparedLock, preparedChannel);
                    if (preparedWorkspace != null) {
                        deleteOwned(preparedWorkspace, candidate);
                    }
                    if (preparedTemporary != null) {
                        deleteOwned(preparedTemporary, candidate);
                    }
                    Files.deleteIfExists(candidateLock);
                    preparedLock = null;
                    preparedChannel = null;
                    preparedWorkspace = null;
                    preparedTemporary = null;
                }
            }
            if (preparedRunId == null) {
                throw new IOException(
                        "Unable to allocate a unique run identifier after "
                                + MAX_ALLOCATION_ATTEMPTS + " attempts");
            }
        } catch (IOException exception) {
            failure = exception;
        }
        if (failure != null) {
            cleanupFailedPreparation(preparedLock, preparedChannel,
                    preparedWorkspace, preparedTemporary, preparedLockPath,
                    failure);
            throw new MavenRuntimeException(
                    "Unable to prepare " + command + " run directory",
                    failure);
        }
        runId = preparedRunId;
        workspaceDirectory = preparedWorkspace;
        temporaryDirectory = preparedTemporary;
        lockPath = preparedLockPath;
        lockChannel = preparedChannel;
        lock = preparedLock;
    }

    private void cleanupFailedPreparation(
            final FileLock preparedLock,
            final FileChannel preparedChannel,
            final Path preparedWorkspace,
            final Path preparedTemporary,
            final Path preparedLockPath,
            final IOException failure) {
        if (preparedLock != null) {
            try {
                preparedLock.release();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        if (preparedChannel != null) {
            try {
                preparedChannel.close();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        try {
            if (preparedWorkspace != null
                    && preparedRunId(preparedWorkspace) != null) {
                deleteOwned(preparedWorkspace,
                        preparedRunId(preparedWorkspace));
            }
            if (preparedTemporary != null
                    && preparedRunId(preparedTemporary) != null) {
                deleteOwned(preparedTemporary,
                        preparedRunId(preparedTemporary));
            }
            if (preparedLockPath != null) {
                Files.deleteIfExists(preparedLockPath);
            }
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private String preparedRunId(final Path path) {
        return path.getFileName() == null
                ? null : path.getFileName().toString();
    }

    private static String newRunId() {
        final byte[] bytes = new byte[RUN_ID_BYTES];
        RANDOM.nextBytes(bytes);
        final StringBuilder value = new StringBuilder(RUN_ID_LENGTH);
        for (byte item : bytes) {
            value.append(String.format("%02x", item & BYTE_MASK));
        }
        return value.toString();
    }

    private static void closeQuietly(final FileLock fileLock,
            final FileChannel channel) {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (IOException ignored) {
            // Best effort after an allocation collision.
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
            // Best effort after an allocation collision.
        }
    }

    private String validateCommand(final String value) {
        if (!"impact".equals(value)
                && !"tree".equals(value)) {
            throw new IllegalArgumentException(
                    "Unsupported subcommand: " + value);
        }
        return value;
    }

    private void recoverStaleRuns(final Path config)
            throws IOException {
        final Set<String> candidates = new LinkedHashSet<>();
        collectOwnedRunIds(workspacesRoot, candidates);
        collectOwnedRunIds(temporaryRoot, candidates);
        for (String candidateId : candidates) {
            final Path candidateLock = config.resolve("locks")
                    .resolve(command + "-"
                            + candidateId + ".lock");
            recoverIfUnlocked(candidateId,
                    workspacesRoot.resolve(candidateId),
                    candidateLock);
        }
    }

    private void collectOwnedRunIds(
            final Path root,
            final Set<String> result) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path candidate : children.toList()) {
                final String identifier = candidate
                        .getFileName().toString();
                if (owned(candidate, identifier)) {
                    result.add(identifier);
                }
            }
        }
    }

    private void recoverIfUnlocked(
            final String candidateId,
            final Path workspace,
            final Path candidateLock) throws IOException {
        try (FileChannel channel = FileChannel.open(
                candidateLock,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            try (FileLock staleLock = channel.tryLock()) {
                if (staleLock == null) {
                    return;
                }
                deleteOwned(workspace, candidateId);
                deleteOwned(temporaryRoot.resolve(candidateId),
                        candidateId);
            } catch (OverlappingFileLockException exception) {
                return;
            }
        }
        Files.deleteIfExists(candidateLock);
    }

    private void writeOwner(final Path directory, final String identifier)
            throws IOException {
        Files.writeString(directory.resolve(OWNER_MARKER),
                ownerValue(identifier), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    private boolean owned(
            final Path directory,
            final String identifier) {
        final Path marker = directory.resolve(OWNER_MARKER);
        try {
            return Files.isRegularFile(marker)
                    && Files.readString(marker,
                    StandardCharsets.UTF_8)
                    .equals(ownerValue(identifier));
        } catch (IOException exception) {
            return false;
        }
    }

    private String ownerValue(final String identifier) {
        return OWNER_PREFIX + command + ":" + identifier;
    }

    private void deleteOwned(
            final Path directory,
            final String identifier) throws IOException {
        if (!owned(directory, identifier)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(
                    Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** @return 12-character hexadecimal run identifier */
    public String getRunId() {
        return runId;
    }

    /** @return command workspace root */
    public Path getWorkspaceDirectory() {
        return workspaceDirectory;
    }

    /** @return command temporary root */
    public Path getTemporaryDirectory() {
        return temporaryDirectory;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            deleteOwned(workspaceDirectory, runId);
            deleteOwned(temporaryDirectory, runId);
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            lock.release();
        } catch (IOException exception) {
            failure = append(failure, exception);
        }
        try {
            lockChannel.close();
        } catch (IOException exception) {
            failure = append(failure, exception);
        }
        try {
            Files.deleteIfExists(lockPath);
        } catch (IOException exception) {
            failure = append(failure, exception);
        }
        if (failure != null) {
            throw new MavenRuntimeException(
                    "Unable to clean " + command
                            + " run directory",
                    failure);
        }
    }

    private IOException append(
            final IOException current,
            final IOException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }
}
