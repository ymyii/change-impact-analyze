package io.github.dependencyanalysis.report.offline;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.report.ScriptSafeJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

// Wiki: wiki/features/report-generator.md - 规范化分片数据
/** Writes bounded callback-based JSON shards for offline HTML reports. */
public final class OfflineShardWriter {

    /** Shard suffix bytes after the record array. */
    private static final int SHARD_SUFFIX_BYTES = 5;

    /** Browser schema version. */
    private final int schemaVersion;

    /** Optional browser schema name. */
    private final String schemaName;

    /** JavaScript callback including the opening parenthesis. */
    private final String callback;

    /** Maximum target bytes for one normal shard. */
    private final int maxShardBytes;

    /** Maximum records in one shard. */
    private final int maxShardRecords;

    /**
     * Creates a writer.
     *
     * @param version browser schema version
     * @param callbackName callback expression without parentheses
     * @param shardBytes maximum target bytes for one normal shard
     */
    public OfflineShardWriter(
            final int version,
            final String callbackName,
            final int shardBytes) {
        this(null, version, callbackName, shardBytes, Integer.MAX_VALUE);
    }

    /**
     * Creates a writer that emits a schema name and version.
     *
     * @param name browser schema name
     * @param version browser schema version
     * @param callbackName callback expression without parentheses
     * @param shardBytes maximum target bytes for one normal shard
     */
    public OfflineShardWriter(
            final String name,
            final int version,
            final String callbackName,
            final int shardBytes) {
        this(name, version, callbackName, shardBytes, Integer.MAX_VALUE);
    }

    /**
     * Creates a writer with a record-count cap.
     *
     * @param version browser schema version
     * @param callbackName callback expression without parentheses
     * @param shardBytes maximum target bytes for one normal shard
     * @param shardRecords maximum records in one shard
     */
    public OfflineShardWriter(
            final int version,
            final String callbackName,
            final int shardBytes,
            final int shardRecords) {
        this(null, version, callbackName, shardBytes, shardRecords);
    }

    /**
     * Creates a schema-named writer with a record-count cap.
     *
     * @param name browser schema name, nullable for legacy schemas
     * @param version browser schema version
     * @param callbackName callback expression without parentheses
     * @param shardBytes maximum target bytes for one normal shard
     * @param shardRecords maximum records in one shard
     */
    public OfflineShardWriter(
            final String name,
            final int version,
            final String callbackName,
            final int shardBytes,
            final int shardRecords) {
        if (version <= 0) {
            throw new IllegalArgumentException(
                    "Schema version must be positive");
        }
        if (callbackName == null || callbackName.isBlank()) {
            throw new IllegalArgumentException(
                    "Shard callback must not be blank");
        }
        if (shardBytes <= 0) {
            throw new IllegalArgumentException(
                    "Shard bytes must be positive");
        }
        if (shardRecords <= 0) {
            throw new IllegalArgumentException(
                    "Shard records must be positive");
        }
        if (name != null && !name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException(
                    "Invalid schema name: " + name);
        }
        schemaVersion = version;
        schemaName = name;
        callback = callbackName + "(";
        maxShardBytes = shardBytes;
        maxShardRecords = shardRecords;
    }

    /**
     * Writes one record kind using contiguous integer IDs.
     *
     * @param kind record kind and filename prefix
     * @param count record count
     * @param records record serializers by ID
     * @param directory physical shard directory
     * @param relativeDirectory page-relative shard directory
     * @return deterministic shard descriptors
     * @throws IOException on write failure
     */
    public List<OfflineShardDescriptor> write(
            final String kind,
            final int count,
            final IntFunction<JsonRecord> records,
            final Path directory,
            final String relativeDirectory) throws IOException {
        return write(kind, count, records, directory,
                relativeDirectory, ignored -> false);
    }

    /**
     * Writes one record kind and honors caller-defined record boundaries.
     *
     * @param kind record kind and filename prefix
     * @param count record count
     * @param records record serializers by ID
     * @param directory physical shard directory
     * @param relativeDirectory page-relative shard directory
     * @param startsShard true when an ID must begin a new shard
     * @return deterministic shard descriptors
     * @throws IOException on write failure
     */
    public List<OfflineShardDescriptor> write(
            final String kind,
            final int count,
            final IntFunction<JsonRecord> records,
            final Path directory,
            final String relativeDirectory,
            final IntPredicate startsShard) throws IOException {
        if (kind == null || !kind.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid shard kind: " + kind);
        }
        if (count < 0) {
            throw new IllegalArgumentException(
                    "Record count must not be negative");
        }
        Files.createDirectories(directory);
        final List<OfflineShardDescriptor> result = new ArrayList<>();
        final List<byte[]> pending = new ArrayList<>();
        int pendingBytes = 0;
        int firstId = 0;
        for (int id = 0; id < count; id++) {
            final byte[] record = serialize(records.apply(id));
            final int wrapper = wrapperBytes(kind, result.size());
            final int separator = pending.isEmpty() ? 0 : 1;
            if (!pending.isEmpty() && (pending.size() >= maxShardRecords
                    || startsShard.test(id) || wrapper + pendingBytes
                    + separator + record.length > maxShardBytes)) {
                result.add(flush(kind, result.size(), firstId,
                        pending, directory, relativeDirectory));
                pending.clear();
                pendingBytes = 0;
                firstId = id;
            }
            pending.add(record);
            pendingBytes += record.length
                    + (pending.size() == 1 ? 0 : 1);
        }
        if (!pending.isEmpty()) {
            result.add(flush(kind, result.size(), firstId,
                    pending, directory, relativeDirectory));
        }
        return List.copyOf(result);
    }

    private OfflineShardDescriptor flush(
            final String kind,
            final int shardId,
            final int firstId,
            final List<byte[]> records,
            final Path directory,
            final String relativeDirectory) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(prefix(kind, shardId));
        for (int index = 0; index < records.size(); index++) {
            if (index > 0) {
                output.write(',');
            }
            output.write(records.get(index));
        }
        output.write("]});\n".getBytes(StandardCharsets.UTF_8));
        final byte[] bytes = output.toByteArray();
        final String file = kind + "-" + String.format(
                Locale.ROOT, "%05d", shardId) + ".js";
        Files.write(directory.resolve(file), bytes);
        return new OfflineShardDescriptor(
                shardId, relativeDirectory + "/" + file,
                firstId, firstId + records.size() - 1,
                records.size(), bytes.length);
    }

    private int wrapperBytes(final String kind, final int shardId) {
        return prefix(kind, shardId).length + SHARD_SUFFIX_BYTES;
    }

    private byte[] prefix(final String kind, final int shardId) {
        final String schema = schemaName == null ? ""
                : "\"schema\":\"" + schemaName + "\",";
        return (callback + "{" + schema
                + "\"schemaVersion\":" + schemaVersion
                + ",\"kind\":\"" + kind + "\",\"shardId\":"
                + shardId + ",\"records\":[")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] serialize(final JsonRecord record) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator json = ScriptSafeJson.factory()
                .createGenerator(output)) {
            record.write(json);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    /** Writes one complete JSON record. */
    @FunctionalInterface
    public interface JsonRecord {
        /**
         * Writes one JSON value.
         *
         * @param json destination
         * @throws IOException on serialization failure
         */
        void write(JsonGenerator json) throws IOException;
    }
}
