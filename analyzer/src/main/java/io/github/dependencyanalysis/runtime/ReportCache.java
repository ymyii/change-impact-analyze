package io.github.dependencyanalysis.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

// Wiki: wiki/c4/components/dependency-analyzer-cli-report-publication.md - 缓存
/** Owned, versioned cache for one command's report pipeline fragments. */
public final class ReportCache implements AutoCloseable {

    /** Internal report-cache schema. */
    public static final int SCHEMA_VERSION = 1;

    /** Cache directory name under an owned command temporary directory. */
    private static final String CACHE_DIRECTORY = "report-cache";

    /** Stable hash character count in fragment filenames. */
    private static final int HASH_LENGTH = 24;

    /** JSON factory. */
    private static final JsonFactory JSON = new JsonFactory();

    /** Owned command temporary directory. */
    private final Path commandTemporaryDirectory;

    /** Dedicated cache root. */
    private final Path root;

    /** Command run identifier. */
    private final String runId;

    /** Command name. */
    private final String command;

    /** Completed fragments. */
    private final List<Fragment> fragments = new ArrayList<>();

    /** Closed state. */
    private boolean closed;

    /**
     * Opens the dedicated cache child for one owned command run.
     *
     * @param run owned command run
     * @param commandName impact or tree
     */
    public ReportCache(
            final CommandRunDirectory run,
            final String commandName) {
        Objects.requireNonNull(run, "run");
        command = requireCommand(commandName);
        runId = run.getRunId();
        commandTemporaryDirectory = run.getTemporaryDirectory()
                .toAbsolutePath().normalize();
        root = commandTemporaryDirectory.resolve(CACHE_DIRECTORY).normalize();
        requireOwnedChild(root);
        try {
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Report cache already exists: " + root);
            }
            Files.createDirectory(root);
            writeManifest(false);
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to prepare report cache", exception);
        }
    }

    /** @return cache root */
    public Path root() {
        return root;
    }

    /**
     * Atomically writes one JSON Lines fragment.
     *
     * @param kind stable fragment kind
     * @param stableKey stable logical key
     * @param records record writers, one JSON object per writer
     * @return completed fragment reference
     */
    public synchronized Fragment writeJsonLines(
            final String kind,
            final String stableKey,
            final List<Consumer<JsonGenerator>> records) {
        return writeJsonLines(kind, stableKey, json -> {
            int count = 0;
            for (Consumer<JsonGenerator> record : records) {
                record.accept(json);
                count++;
            }
            return count;
        });
    }

    /**
     * Atomically streams one JSON Lines fragment without materializing
     * record-writer objects.
     *
     * @param kind stable fragment kind
     * @param stableKey stable logical key
     * @param records streaming record producer
     * @return completed fragment reference
     */
    public synchronized Fragment writeJsonLines(
            final String kind,
            final String stableKey,
            final JsonLinesWriter records) {
        requireOpen();
        final String safeKind = requireToken(kind);
        final String hash = hash(safeKind + "\n" + stableKey);
        final String fileName = safeKind + "-" + hash + ".jsonl";
        final Path target = root.resolve(fileName).normalize();
        requireOwnedChild(target);
        final Path temporary = root.resolve(fileName + ".tmp-"
                + UUID.randomUUID()).normalize();
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Duplicate cache fragment: " + target);
            }
            final int recordCount;
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8);
                 JsonGenerator json = JSON.createGenerator(writer)) {
                json.setRootValueSeparator(new SerializedString("\n"));
                recordCount = records.write(json);
                if (recordCount < 0) {
                    throw new IOException(
                            "Negative report cache record count");
                }
            }
            move(temporary, target);
            final Path marker = completeMarker(target);
            final Path markerTemporary = root.resolve(
                    marker.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.writeString(markerTemporary,
                    "schema=" + SCHEMA_VERSION + "\n",
                    StandardCharsets.UTF_8);
            move(markerTemporary, marker);
            final Fragment fragment = new Fragment(
                    safeKind, stableKey, target, recordCount);
            fragments.add(fragment);
            fragments.sort(Comparator.comparing(Fragment::kind)
                    .thenComparing(Fragment::stableKey));
            writeManifest(false);
            return fragment;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(temporary);
                Files.deleteIfExists(target);
                Files.deleteIfExists(completeMarker(target));
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw new MavenRuntimeException(
                    "Unable to write report cache fragment", exception);
        }
    }

    /** @return stable completed fragments */
    public synchronized List<Fragment> fragments() {
        requireOpen();
        validateFragments();
        return List.copyOf(fragments);
    }

    /**
     * Deletes completed fragments for one already-published logical scope.
     *
     * @param stableKeyPrefix exact stable-key prefix
     */
    public synchronized void discard(final String stableKeyPrefix) {
        requireOpen();
        final List<Fragment> removed = fragments.stream()
                .filter(value -> value.stableKey()
                        .startsWith(stableKeyPrefix)).toList();
        try {
            for (Fragment fragment : removed) {
                requireOwnedChild(fragment.path());
                Files.deleteIfExists(fragment.path());
                Files.deleteIfExists(fragment.completeMarker());
            }
            fragments.removeAll(removed);
            writeManifest(false);
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to discard published report cache fragments",
                    exception);
        }
    }

    /** Marks the manifest complete after all planned fragments exist. */
    public synchronized void complete() {
        requireOpen();
        try {
            validateManifest();
            validateFragments();
            writeManifest(true);
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to complete report cache manifest", exception);
        }
    }

    /** Deletes the current owned cache child on success or failure. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            deleteVerifiedRoot();
            closed = true;
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to clean report cache", exception);
        }
    }

    private void writeManifest(final boolean complete) throws IOException {
        final Path target = root.resolve("manifest.json");
        final Path temporary = root.resolve("manifest.json.tmp-"
                + UUID.randomUUID());
        try (JsonGenerator json = JSON.createGenerator(
                Files.newBufferedWriter(temporary,
                        StandardCharsets.UTF_8))) {
            json.useDefaultPrettyPrinter();
            json.writeStartObject();
            json.writeNumberField("schemaVersion", SCHEMA_VERSION);
            json.writeStringField("runId", runId);
            json.writeStringField("command", command);
            json.writeBooleanField("complete", complete);
            json.writeArrayFieldStart("fragments");
            for (Fragment fragment : fragments) {
                json.writeStartObject();
                json.writeStringField("kind", fragment.kind());
                json.writeStringField("stableKey", fragment.stableKey());
                json.writeStringField("file",
                        fragment.path().getFileName().toString());
                json.writeNumberField("recordCount",
                        fragment.recordCount());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        move(temporary, target);
    }

    private void deleteVerifiedRoot() throws IOException {
        requireOwnedChild(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe report cache root: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> ownedPaths = paths.toList();
            for (Path path : ownedPaths) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException(
                            "Unsafe symbolic link in report cache: " + path);
                }
            }
            for (Path path : ownedPaths.stream()
                    .sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void validateFragments() {
        try {
            for (Fragment fragment : fragments) {
                requireOwnedChild(fragment.path());
                if (Files.isSymbolicLink(fragment.path())
                        || !Files.isRegularFile(fragment.path(),
                        LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException(
                            "Missing report cache fragment: "
                                    + fragment.path());
                }
                final Path marker = fragment.completeMarker();
                requireOwnedChild(marker);
                if (Files.isSymbolicLink(marker)
                        || !Files.isRegularFile(marker,
                        LinkOption.NOFOLLOW_LINKS)
                        || !Files.readString(marker,
                        StandardCharsets.UTF_8).equals(
                                "schema=" + SCHEMA_VERSION + "\n")) {
                    throw new IOException(
                            "Incomplete report cache fragment: "
                                    + fragment.path());
                }
                validateFragmentContents(fragment);
            }
        } catch (IOException exception) {
            throw new MavenRuntimeException(
                    "Invalid report cache fragment", exception);
        }
    }

    private void validateFragmentContents(final Fragment fragment)
            throws IOException {
        int count = 0;
        try (JsonParser json = JSON.createParser(
                Files.newBufferedReader(fragment.path(),
                        StandardCharsets.UTF_8))) {
            JsonToken token;
            while ((token = json.nextToken()) != null) {
                if (token != JsonToken.START_OBJECT) {
                    throw new IOException(
                            "Invalid report cache JSON Lines record: "
                                    + fragment.path());
                }
                json.skipChildren();
                count++;
            }
        }
        if (count != fragment.recordCount()) {
            throw new IOException("Report cache record count mismatch: "
                    + fragment.path());
        }
    }

    private void validateManifest() throws IOException {
        final Path manifest = root.resolve("manifest.json");
        if (Files.isSymbolicLink(manifest)
                || !Files.isRegularFile(manifest,
                LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing report cache manifest");
        }
        int schema = -1;
        String manifestRun = null;
        String manifestCommand = null;
        try (JsonParser json = JSON.createParser(
                Files.newBufferedReader(manifest,
                        StandardCharsets.UTF_8))) {
            if (json.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Invalid report cache manifest");
            }
            while (json.nextToken() != JsonToken.END_OBJECT) {
                final String name = json.currentName();
                json.nextToken();
                if ("schemaVersion".equals(name)) {
                    schema = json.getIntValue();
                } else if ("runId".equals(name)) {
                    manifestRun = json.getValueAsString();
                } else if ("command".equals(name)) {
                    manifestCommand = json.getValueAsString();
                } else {
                    json.skipChildren();
                }
            }
            if (json.nextToken() != null) {
                throw new IOException(
                        "Trailing report cache manifest content");
            }
        }
        if (schema != SCHEMA_VERSION || !runId.equals(manifestRun)
                || !command.equals(manifestCommand)) {
            throw new IOException("Report cache manifest ownership or "
                    + "schema mismatch");
        }
    }

    private void requireOwnedChild(final Path value) {
        final Path normalized = value.toAbsolutePath().normalize();
        if (!normalized.startsWith(commandTemporaryDirectory)
                || normalized.equals(commandTemporaryDirectory)
                || !rootParent(normalized).startsWith(
                commandTemporaryDirectory)) {
            throw new IllegalArgumentException(
                    "Cache path escapes owned command directory: " + value);
        }
    }

    private Path rootParent(final Path value) {
        Path current = value;
        while (current.getParent() != null
                && !current.getParent().equals(commandTemporaryDirectory)) {
            current = current.getParent();
        }
        return current.getParent() == null ? current : current.getParent();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Report cache is closed");
        }
    }

    private static String requireCommand(final String value) {
        if (!"impact".equals(value) && !"tree".equals(value)) {
            throw new IllegalArgumentException(
                    "Unsupported report cache command: " + value);
        }
        return value;
    }

    private static String requireToken(final String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException(
                    "Invalid cache fragment kind: " + value);
        }
        return value;
    }

    private static String hash(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Path completeMarker(final Path fragment) {
        return fragment.resolveSibling(
                fragment.getFileName().toString() + ".complete");
    }

    private static void move(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Completed cache fragment reference.
     *
     * @param kind fragment kind
     * @param stableKey logical fragment identity
     * @param path completed JSON Lines path
     * @param recordCount record count
     */
    public record Fragment(
            String kind,
            String stableKey,
            Path path,
            int recordCount) {

        /** Validates one completed fragment reference. */
        public Fragment {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(stableKey, "stableKey");
            Objects.requireNonNull(path, "path");
            if (recordCount < 0) {
                throw new IllegalArgumentException("recordCount < 0");
            }
        }

        /** @return complete marker path */
        public Path completeMarker() {
            return ReportCache.completeMarker(path);
        }
    }

    /** Writes root JSON values and returns the written record count. */
    @FunctionalInterface
    public interface JsonLinesWriter {

        /**
         * Writes zero or more JSON object records.
         *
         * @param json fragment generator
         * @return record count
         * @throws IOException on write failure
         */
        int write(JsonGenerator json) throws IOException;
    }
}
