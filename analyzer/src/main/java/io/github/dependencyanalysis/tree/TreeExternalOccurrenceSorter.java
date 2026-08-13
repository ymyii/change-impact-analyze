package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/** Cache-backed stable grouping for large Tree dependency occurrence sets. */
final class TreeExternalOccurrenceSorter {

    /** Maximum records retained before one sorted spill. */
    static final int MAX_BATCH_RECORDS = 10_000;

    /** Approximate payload limit retained before one sorted spill. */
    static final long MAX_BATCH_BYTES = 8L * 1024L * 1024L;

    /** Maximum open merge inputs. */
    static final int MAX_MERGE_WAYS = 32;

    /** Radix used in stable scope hashes. */
    private static final int HASH_RADIX = 16;

    /** Approximate JSON/index overhead per record. */
    private static final long RECORD_OVERHEAD_BYTES = 32L;

    /** JSON factory. */
    private static final JsonFactory JSON = new JsonFactory();

    /** Sort order independent of source iteration/hash order. */
    private static final Comparator<IndexRecord> ORDER = Comparator
            .comparing(IndexRecord::key)
            .thenComparingInt(IndexRecord::moduleIndex)
            .thenComparingInt(IndexRecord::occurrenceIndex);

    /** Batch record limit. */
    private final int recordLimit;

    /** Batch payload limit. */
    private final long byteLimit;

    /** Merge fan-in. */
    private final int mergeWays;

    TreeExternalOccurrenceSorter() {
        this(MAX_BATCH_RECORDS, MAX_BATCH_BYTES, MAX_MERGE_WAYS);
    }

    TreeExternalOccurrenceSorter(
            final int records,
            final long bytes,
            final int ways) {
        if (records < 1 || bytes < 1L || ways < 2) {
            throw new IllegalArgumentException(
                    "External sort limits must be positive");
        }
        recordLimit = records;
        byteLimit = bytes;
        mergeWays = ways;
    }

    /**
     * Streams stable dependency-key groups through a bounded external sort.
     *
     * @param modules source modules
     * @param selectedOnly whether omitted occurrences are excluded
     * @param cacheRoot task cache root
     * @param scope stable grouping scope
     * @param consumer group consumer
     * @throws IOException on cache I/O failure
     */
    void group(
            final List<ModuleTreeResult> modules,
            final boolean selectedOnly,
            final Path cacheRoot,
            final String scope,
            final BiConsumer<DependencyKey, List<OccurrenceRef>> consumer)
            throws IOException {
        final Path directory = cacheRoot.resolve("sort-"
                + Integer.toUnsignedString(scope.hashCode(), HASH_RADIX) + "-"
                + UUID.randomUUID()).normalize();
        if (!directory.startsWith(cacheRoot.toAbsolutePath().normalize())) {
            throw new IOException("External sort path escaped cache root");
        }
        Files.createDirectory(directory);
        try {
            List<Path> chunks = spill(modules, selectedOnly, directory);
            if (chunks.isEmpty()) {
                return;
            }
            int round = 0;
            while (chunks.size() > 1) {
                final List<Path> merged = new ArrayList<>();
                for (int start = 0; start < chunks.size();
                     start += mergeWays) {
                    final int end = Math.min(chunks.size(),
                            start + mergeWays);
                    merged.add(merge(directory, round, merged.size(),
                            chunks.subList(start, end)));
                }
                deleteFragments(chunks);
                chunks = merged;
                round++;
            }
            consume(chunks.get(0), modules, consumer);
        } finally {
            deleteTree(directory);
        }
    }

    private List<Path> spill(
            final List<ModuleTreeResult> modules,
            final boolean selectedOnly,
            final Path directory) throws IOException {
        final List<Path> chunks = new ArrayList<>();
        final List<IndexRecord> batch = new ArrayList<>();
        long bytes = 0L;
        for (int moduleIndex = 0; moduleIndex < modules.size();
             moduleIndex++) {
            final List<DependencyOccurrence> occurrences = modules
                    .get(moduleIndex).getOccurrences();
            for (int occurrenceIndex = 0;
                 occurrenceIndex < occurrences.size(); occurrenceIndex++) {
                final DependencyOccurrence occurrence = occurrences.get(
                        occurrenceIndex);
                if (selectedOnly && !occurrence.isSelected()) {
                    continue;
                }
                final IndexRecord record = new IndexRecord(
                        occurrence.getKey().toString(), moduleIndex,
                        occurrenceIndex);
                batch.add(record);
                bytes += payloadBytes(record, occurrence);
                if (batch.size() >= recordLimit || bytes >= byteLimit) {
                    chunks.add(writeChunk(directory, chunks.size(), batch));
                    batch.clear();
                    bytes = 0L;
                }
            }
        }
        if (!batch.isEmpty()) {
            chunks.add(writeChunk(directory, chunks.size(), batch));
        }
        return chunks;
    }

    private long payloadBytes(
            final IndexRecord record,
            final DependencyOccurrence occurrence) {
        long result = record.key().length() * 2L + RECORD_OVERHEAD_BYTES;
        for (String node : occurrence.getPath()) {
            result += node.length() * 2L;
        }
        return result;
    }

    private Path writeChunk(
            final Path directory,
            final int index,
            final List<IndexRecord> values) throws IOException {
        final Path target = directory.resolve(String.format(
                java.util.Locale.ROOT, "chunk-%06d.jsonl", index));
        final List<IndexRecord> ordered = values.stream()
                .sorted(ORDER).toList();
        write(target, ordered);
        return target;
    }

    private Path merge(
            final Path directory,
            final int round,
            final int index,
            final List<Path> inputs) throws IOException {
        final Path target = directory.resolve(String.format(
                java.util.Locale.ROOT, "merge-%04d-%06d.jsonl",
                round, index));
        final Path temporary = temporary(target);
        final List<RecordCursor> cursors = new ArrayList<>();
        final PriorityQueue<RecordCursor> queue = new PriorityQueue<>(
                Comparator.comparing(RecordCursor::current, ORDER));
        try {
            for (Path input : inputs) {
                requireComplete(input);
                final RecordCursor cursor = new RecordCursor(input);
                cursors.add(cursor);
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
            try (JsonGenerator json = JSON.createGenerator(
                    Files.newBufferedWriter(temporary,
                            StandardCharsets.UTF_8))) {
                json.setRootValueSeparator(new SerializedString("\n"));
                while (!queue.isEmpty()) {
                    final RecordCursor cursor = queue.remove();
                    writeRecord(json, cursor.current());
                    if (cursor.advance()) {
                        queue.add(cursor);
                    }
                }
            }
            complete(temporary, target);
            return target;
        } finally {
            for (RecordCursor cursor : cursors) {
                cursor.close();
            }
            Files.deleteIfExists(temporary);
        }
    }

    private void write(
            final Path target,
            final List<IndexRecord> values) throws IOException {
        final Path temporary = temporary(target);
        try {
            try (JsonGenerator json = JSON.createGenerator(
                    Files.newBufferedWriter(temporary,
                            StandardCharsets.UTF_8))) {
                json.setRootValueSeparator(new SerializedString("\n"));
                for (IndexRecord value : values) {
                    writeRecord(json, value);
                }
            }
            complete(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void consume(
            final Path source,
            final List<ModuleTreeResult> modules,
            final BiConsumer<DependencyKey, List<OccurrenceRef>> consumer)
            throws IOException {
        requireComplete(source);
        try (RecordCursor cursor = new RecordCursor(source)) {
            String currentKey = null;
            final List<OccurrenceRef> group = new ArrayList<>();
            while (cursor.advance()) {
                final IndexRecord record = cursor.current();
                if (currentKey != null && !currentKey.equals(record.key())) {
                    consumer.accept(group.get(0).occurrence().getKey(),
                            List.copyOf(group));
                    group.clear();
                }
                currentKey = record.key();
                final ModuleTreeResult module = modules.get(
                        record.moduleIndex());
                group.add(new OccurrenceRef(module,
                        module.getOccurrences().get(
                                record.occurrenceIndex())));
            }
            if (!group.isEmpty()) {
                consumer.accept(group.get(0).occurrence().getKey(),
                        List.copyOf(group));
            }
        }
    }

    private void writeRecord(
            final JsonGenerator json,
            final IndexRecord value) throws IOException {
        json.writeStartObject();
        json.writeStringField("key", value.key());
        json.writeNumberField("module", value.moduleIndex());
        json.writeNumberField("occurrence", value.occurrenceIndex());
        json.writeEndObject();
    }

    private void complete(final Path temporary, final Path target)
            throws IOException {
        move(temporary, target);
        Files.writeString(marker(target), "complete\n",
                StandardCharsets.UTF_8);
    }

    private void requireComplete(final Path value) throws IOException {
        if (!Files.isRegularFile(value)
                || !Files.isRegularFile(marker(value))) {
            throw new IOException("Incomplete external sort fragment: "
                    + value);
        }
    }

    private void deleteFragments(final List<Path> values) throws IOException {
        for (Path value : values) {
            Files.deleteIfExists(value);
            Files.deleteIfExists(marker(value));
        }
    }

    private void deleteTree(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Path temporary(final Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp-"
                + UUID.randomUUID());
    }

    private Path marker(final Path value) {
        return value.resolveSibling(value.getFileName() + ".complete");
    }

    private void move(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    /**
     * Index-only stable record written to JSON Lines.
     *
     * @param key dependency conflict key
     * @param moduleIndex owning Module index
     * @param occurrenceIndex occurrence index within the Module
     */
    private record IndexRecord(
            String key,
            int moduleIndex,
            int occurrenceIndex) {
    }

    /**
     * Rehydrated occurrence and owning Module.
     *
     * @param module owning Module
     * @param occurrence dependency occurrence
     */
    record OccurrenceRef(
            ModuleTreeResult module,
            DependencyOccurrence occurrence) {
    }

    /** Streaming JSON Lines record cursor. */
    private static final class RecordCursor implements AutoCloseable {

        /** Parser. */
        private final JsonParser parser;

        /** Current record. */
        private IndexRecord current;

        RecordCursor(final Path source) throws IOException {
            parser = JSON.createParser(Files.newBufferedReader(
                    source, StandardCharsets.UTF_8));
        }

        boolean advance() throws IOException {
            final JsonToken token = parser.nextToken();
            if (token == null) {
                current = null;
                return false;
            }
            if (token != JsonToken.START_OBJECT) {
                throw new IOException("Invalid external sort JSON Lines");
            }
            String key = null;
            int module = -1;
            int occurrence = -1;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String name = parser.currentName();
                parser.nextToken();
                if ("key".equals(name)) {
                    key = parser.getValueAsString();
                } else if ("module".equals(name)) {
                    module = parser.getIntValue();
                } else if ("occurrence".equals(name)) {
                    occurrence = parser.getIntValue();
                } else {
                    parser.skipChildren();
                }
            }
            if (key == null || module < 0 || occurrence < 0) {
                throw new IOException("Invalid external sort record");
            }
            current = new IndexRecord(key, module, occurrence);
            return true;
        }

        IndexRecord current() {
            return current;
        }

        @Override
        public void close() throws IOException {
            parser.close();
        }
    }
}
