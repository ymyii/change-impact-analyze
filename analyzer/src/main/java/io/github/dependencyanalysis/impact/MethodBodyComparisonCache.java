package io.github.dependencyanalysis.impact;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import io.github.dependencyanalysis.bytecode.BytecodeDiffResult;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.DecompileComparisonEvidence;
import io.github.dependencyanalysis.bytecode.DecompileComparisonStatus;
import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MethodBodySuppressionReason;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.bytecode.SsaComparisonStatus;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.runtime.ReportCache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md
// - Command-owned method body comparison cache boundary
/** Stores pair-local decompiled method text for later Code comparison. */
final class MethodBodyComparisonCache {

    /** Cache fragment record schema. */
    private static final int RECORD_SCHEMA_VERSION = 2;

    /** Cache status when Java equality short-circuits normalized SSA. */
    private static final String SSA_NOT_EXECUTED = "NOT_EXECUTED";

    /** Cache reason when Java equality short-circuits normalized SSA. */
    private static final String SSA_SHORT_CIRCUIT_REASON =
            "JAVA_TEXT_IDENTICAL_SHORT_CIRCUIT";

    /** Report cache fragment kind. */
    private static final String FRAGMENT_KIND = "method-body-comparison";

    /** Streaming JSON factory. */
    private static final JsonFactory JSON = new JsonFactory();

    /** Required fields in one complete comparison record. */
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "schemaVersion", "oldArtifact", "newArtifact", "owner",
            "name", "descriptor", "oldHash", "newHash",
            "oldMajorVersion", "newMajorVersion", "ssaExecuted", "ssaStatus",
            "ssaReason", "ssaElapsedMillis", "decompileStatus",
            "decompileReason", "decompileElapsedMillis", "oldAvailable",
            "oldSource", "oldFailureReason", "newAvailable", "newSource",
            "newFailureReason", "suppressionReasons");

    /** Command-owned report cache. */
    private final ReportCache reportCache;

    /** Completed fragment by logical coordinate pair. */
    private final Map<String, ReportCache.Fragment> fragments =
            new ConcurrentHashMap<>();

    /** Lazily parsed record maps by logical coordinate pair. */
    private final Map<String, Map<String, CachedComparison>> loaded =
            new ConcurrentHashMap<>();

    /** Unified diff producer. */
    private final UnifiedDiffGenerator diffs = new UnifiedDiffGenerator();

    /** Whether command cache fragments passed global integrity validation. */
    private volatile boolean validated;

    /** @param cache command-owned report cache */
    MethodBodyComparisonCache(final ReportCache cache) {
        reportCache = Objects.requireNonNull(cache, "reportCache");
    }

    /**
     * Writes one stable logical pair fragment.
     *
     * @param change logical dependency upgrade
     * @param result completed bytecode semantic comparisons
     */
    void write(
            final DependencyChange change,
            final BytecodeDiffResult result) {
        if (validated) {
            throw new MethodBodyCacheException(
                    "Method body comparison cache is already readable");
        }
        final String pair = pairKey(change);
        final Map<String, SsaComparisonEvidence> ssaByMethod =
                new LinkedHashMap<>();
        result.ssaComparisons().forEach(value -> ssaByMethod.put(
                value.stableKey(), value));
        final List<DecompileComparisonEvidence> comparisons = result
                .decompileComparisons().stream()
                .sorted(Comparator.comparing(
                        DecompileComparisonEvidence::stableKey)).toList();
        final List<Consumer<JsonGenerator>> records = new ArrayList<>();
        for (DecompileComparisonEvidence comparison : comparisons) {
            final SsaComparisonEvidence ssa = ssaByMethod.get(
                    comparison.stableKey());
            if (ssa == null && comparison.getStatus()
                    != DecompileComparisonStatus.IDENTICAL) {
                throw new MethodBodyCacheException(
                        "Missing SSA evidence for cache record: "
                                + comparison.stableKey());
            }
            if (ssa != null && comparison.getStatus()
                    == DecompileComparisonStatus.IDENTICAL) {
                throw new MethodBodyCacheException(
                        "SSA evidence exists after Java short circuit: "
                                + comparison.stableKey());
            }
            records.add(json -> writeRecord(json, comparison, ssa));
        }
        try {
            final ReportCache.Fragment fragment = reportCache.writeJsonLines(
                    FRAGMENT_KIND, pair, records);
            if (fragments.putIfAbsent(pair, fragment) != null) {
                throw new MethodBodyCacheException(
                        "Duplicate method body cache pair: " + pair);
            }
        } catch (MethodBodyCacheException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MethodBodyCacheException(
                    "Unable to write method body comparison cache: " + pair,
                    exception);
        }
    }

    private void writeRecord(
            final JsonGenerator json,
            final DecompileComparisonEvidence comparison,
            final SsaComparisonEvidence ssa) {
        try {
            final ChangePoint point = comparison.getChangePoint();
            json.writeStartObject();
            json.writeNumberField("schemaVersion", RECORD_SCHEMA_VERSION);
            json.writeStringField("oldArtifact",
                    comparison.getOldArtifact().toString());
            json.writeStringField("newArtifact",
                    comparison.getNewArtifact().toString());
            json.writeStringField("owner", point.getOwner());
            json.writeStringField("name", point.getName());
            json.writeStringField("descriptor", point.getOldDescriptor());
            json.writeStringField("oldHash", point.getOldHash());
            json.writeStringField("newHash", point.getNewHash());
            json.writeNumberField("oldMajorVersion",
                    comparison.getOldMajorVersion());
            json.writeNumberField("newMajorVersion",
                    comparison.getNewMajorVersion());
            final boolean ssaExecuted = ssa != null;
            json.writeBooleanField("ssaExecuted", ssaExecuted);
            json.writeStringField("ssaStatus", ssaExecuted
                    ? ssa.getStatus().name() : SSA_NOT_EXECUTED);
            json.writeStringField("ssaReason", ssaExecuted
                    ? ssa.getReason() : SSA_SHORT_CIRCUIT_REASON);
            json.writeNumberField("ssaElapsedMillis", ssaExecuted
                    ? ssa.getElapsedMillis() : 0L);
            json.writeStringField("decompileStatus",
                    comparison.getStatus().name());
            json.writeStringField("decompileReason",
                    comparison.getReason());
            json.writeNumberField("decompileElapsedMillis",
                    comparison.getElapsedMillis());
            writeMethod(json, "old", comparison.getOldMethod());
            writeMethod(json, "new", comparison.getNewMethod());
            json.writeArrayFieldStart("suppressionReasons");
            comparison.getSuppressionReasons().stream()
                    .sorted().forEach(value -> writeString(json,
                            value.name()));
            json.writeEndArray();
            json.writeEndObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeMethod(
            final JsonGenerator json,
            final String prefix,
            final DecompiledMethod method) throws IOException {
        json.writeBooleanField(prefix + "Available", method.isAvailable());
        if (method.isAvailable()) {
            json.writeStringField(prefix + "Source", method.getSource());
            json.writeNullField(prefix + "FailureReason");
        } else {
            json.writeNullField(prefix + "Source");
            json.writeStringField(prefix + "FailureReason",
                    method.getFailureReason());
        }
    }

    private void writeString(
            final JsonGenerator json,
            final String value) {
        try {
            json.writeString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Builds report evidence without invoking Vineflower again.
     *
     * @param bound retained method body change
     * @return cached Code comparison evidence
     */
    CodeComparisonEvidence codeComparison(final BoundChangePoint bound) {
        final ChangePoint point = bound.getChangePoint();
        if (point.getKind() != ChangePointKind.METHOD_BODY_CHANGED) {
            throw new IllegalArgumentException(
                    "Cache requires METHOD_BODY_CHANGED");
        }
        final String pair = pairKey(bound.getDependencyUpgradeKey());
        final CachedComparison comparison = records(pair).get(
                comparisonKey(pair, point));
        if (comparison == null) {
            throw new MethodBodyCacheException(
                    "Missing cached method body comparison: "
                            + comparisonKey(pair, point));
        }
        if (comparison.status() == DecompileComparisonStatus.UNKNOWN) {
            return new CodeComparisonEvidence(
                    CodeComparisonStatus.UNAVAILABLE, List.of(),
                    comparison.unavailableReason());
        }
        if (comparison.status() == DecompileComparisonStatus.IDENTICAL) {
            throw new MethodBodyCacheException(
                    "Suppressed identical method reached report cache: "
                            + comparisonKey(pair, point));
        }
        if (comparison.oldSource() == null
                || comparison.newSource() == null) {
            throw new MethodBodyCacheException(
                    "Differing cache record has unavailable source: "
                            + comparisonKey(pair, point));
        }
        final List<UnifiedDiffHunk> hunks = diffs.diff(
                comparison.oldSource(), comparison.newSource());
        if (hunks.isEmpty()) {
            throw new MethodBodyCacheException(
                    "Differing cache record produced no diff: "
                            + comparisonKey(pair, point));
        }
        return new CodeComparisonEvidence(
                CodeComparisonStatus.AVAILABLE, hunks, "");
    }

    private Map<String, CachedComparison> records(final String pair) {
        try {
            return loaded.computeIfAbsent(pair, this::load);
        } catch (MethodBodyCacheException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MethodBodyCacheException(
                    "Unable to read method body comparison cache: " + pair,
                    exception);
        }
    }

    private Map<String, CachedComparison> load(final String pair) {
        validateFragmentsOnce();
        final ReportCache.Fragment fragment = fragments.get(pair);
        if (fragment == null) {
            throw new MethodBodyCacheException(
                    "Missing method body cache fragment: " + pair);
        }
        final Map<String, CachedComparison> result = new LinkedHashMap<>();
        try (JsonParser json = JSON.createParser(Files.newBufferedReader(
                fragment.path(), StandardCharsets.UTF_8))) {
            while (json.nextToken() != null) {
                if (json.currentToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Invalid cache record root");
                }
                final CachedComparison record = readRecord(json, pair);
                final String key = comparisonKey(pair, record.owner(),
                        record.name(), record.descriptor());
                if (result.putIfAbsent(key, record) != null) {
                    throw new IOException("Duplicate cache record: " + key);
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new MethodBodyCacheException(
                    "Invalid method body cache fragment: " + pair,
                    exception);
        }
        return Map.copyOf(result);
    }

    private void validateFragmentsOnce() {
        if (validated) {
            return;
        }
        synchronized (this) {
            if (!validated) {
                reportCache.fragments();
                validated = true;
            }
        }
    }

    private CachedComparison readRecord(
            final JsonParser json,
            final String expectedPair) throws IOException {
        final CacheRecordBuilder builder = new CacheRecordBuilder();
        while (json.nextToken() != JsonToken.END_OBJECT) {
            final String field = json.currentName();
            json.nextToken();
            builder.read(field, json);
        }
        return builder.build(expectedPair);
    }

    /** Mutable parser state scoped to one cache record. */
    private final class CacheRecordBuilder {

        /** Record schema. */
        private int schema = -1;

        /** Baseline artifact. */
        private String oldArtifact;

        /** Target artifact. */
        private String newArtifact;

        /** Internal class owner. */
        private String owner;

        /** Method name. */
        private String name;

        /** JVM method descriptor. */
        private String descriptor;

        /** Baseline body hash. */
        private String oldHash;

        /** Target body hash. */
        private String newHash;

        /** Baseline class major version. */
        private int oldMajorVersion = -1;

        /** Target class major version. */
        private int newMajorVersion = -1;

        /** Normalized SSA status. */
        private String ssaStatus;

        /** Whether normalized SSA actually executed. */
        private Boolean ssaExecuted;

        /** Normalized SSA reason. */
        private String ssaReason;

        /** Normalized SSA elapsed milliseconds. */
        private long ssaElapsedMillis = -1L;

        /** Decompiled Java status. */
        private String status;

        /** Decompiled Java reason. */
        private String decompileReason;

        /** Decompiled Java elapsed milliseconds. */
        private long decompileElapsedMillis = -1L;

        /** Baseline source availability. */
        private Boolean oldAvailable;

        /** Target source availability. */
        private Boolean newAvailable;

        /** Baseline source. */
        private String oldSource;

        /** Target source. */
        private String newSource;

        /** Baseline failure reason. */
        private String oldFailure;

        /** Target failure reason. */
        private String newFailure;

        /** Union-filter suppression reasons. */
        private EnumSet<MethodBodySuppressionReason> suppressionReasons =
                EnumSet.noneOf(MethodBodySuppressionReason.class);

        /** Encountered JSON fields. */
        private final Set<String> fields = new HashSet<>();

        void read(
                final String field,
                final JsonParser json) throws IOException {
            if (!fields.add(field)) {
                throw new IOException("Duplicate cache field: " + field);
            }
            switch (field) {
                case "schemaVersion" -> schema(json.getIntValue());
                case "oldArtifact" -> oldArtifact(json.getValueAsString());
                case "newArtifact" -> newArtifact(json.getValueAsString());
                case "owner" -> owner(json.getValueAsString());
                case "name" -> name(json.getValueAsString());
                case "descriptor" -> descriptor(json.getValueAsString());
                case "oldHash" -> oldHash(json.getValueAsString());
                case "newHash" -> newHash(json.getValueAsString());
                case "oldMajorVersion" -> oldMajorVersion(
                        json.getIntValue());
                case "newMajorVersion" -> newMajorVersion(
                        json.getIntValue());
                case "ssaExecuted" -> ssaExecuted(json.getBooleanValue());
                case "ssaStatus" -> ssaStatus(json.getValueAsString());
                case "ssaReason" -> ssaReason(json.getValueAsString());
                case "ssaElapsedMillis" -> ssaElapsedMillis(
                        json.getLongValue());
                case "decompileStatus" -> status(json.getValueAsString());
                case "decompileReason" -> decompileReason(
                        json.getValueAsString());
                case "decompileElapsedMillis" -> decompileElapsedMillis(
                        json.getLongValue());
                case "oldAvailable" -> oldAvailable(
                        json.getBooleanValue());
                case "newAvailable" -> newAvailable(
                        json.getBooleanValue());
                case "oldSource" -> oldSource(nullableText(json));
                case "newSource" -> newSource(nullableText(json));
                case "oldFailureReason" -> oldFailure(nullableText(json));
                case "newFailureReason" -> newFailure(nullableText(json));
                case "suppressionReasons" -> suppressionReasons(
                        readSuppressionReasons(json));
                default -> json.skipChildren();
            }
        }

        CachedComparison build(final String expectedPair)
                throws IOException {
            final String actualPair = oldArtifact + "->" + newArtifact;
            if (!fields.containsAll(REQUIRED_FIELDS)
                    || schema != RECORD_SCHEMA_VERSION
                    || !expectedPair.equals(actualPair)
                    || blank(owner) || blank(name) || blank(descriptor)
                    || blank(oldHash) || blank(newHash)
                    || oldMajorVersion < 0 || newMajorVersion < 0
                    || ssaExecuted == null || blank(ssaStatus)
                    || blank(ssaReason)
                    || ssaElapsedMillis < 0L || blank(status)
                    || blank(decompileReason)
                    || decompileElapsedMillis < 0L
                    || oldAvailable == null || newAvailable == null) {
                throw new IOException(
                        "Invalid cache record identity or schema");
            }
            final DecompileComparisonStatus parsed =
                    parseDecompileStatus(status);
            validateMethodSide("old", oldAvailable, oldSource, oldFailure);
            validateMethodSide("new", newAvailable, newSource, newFailure);
            validateSsaComparison(ssaExecuted, ssaStatus, ssaReason,
                    ssaElapsedMillis, parsed, suppressionReasons);
            validateJavaComparison(parsed,
                    oldAvailable, newAvailable, oldSource, newSource,
                    suppressionReasons);
            return new CachedComparison(owner, name, descriptor, parsed,
                    oldSource, newSource, oldFailure, newFailure);
        }

        private void schema(final int value) {
            schema = value;
        }

        private void oldArtifact(final String value) {
            oldArtifact = value;
        }

        private void newArtifact(final String value) {
            newArtifact = value;
        }

        private void owner(final String value) {
            owner = value;
        }

        private void name(final String value) {
            name = value;
        }

        private void descriptor(final String value) {
            descriptor = value;
        }

        private void oldHash(final String value) {
            oldHash = value;
        }

        private void newHash(final String value) {
            newHash = value;
        }

        private void oldMajorVersion(final int value) {
            oldMajorVersion = value;
        }

        private void newMajorVersion(final int value) {
            newMajorVersion = value;
        }

        private void ssaStatus(final String value) {
            ssaStatus = value;
        }

        private void ssaExecuted(final boolean value) {
            ssaExecuted = value;
        }

        private void ssaReason(final String value) {
            ssaReason = value;
        }

        private void ssaElapsedMillis(final long value) {
            ssaElapsedMillis = value;
        }

        private void status(final String value) {
            status = value;
        }

        private void decompileReason(final String value) {
            decompileReason = value;
        }

        private void decompileElapsedMillis(final long value) {
            decompileElapsedMillis = value;
        }

        private void oldAvailable(final boolean value) {
            oldAvailable = value;
        }

        private void newAvailable(final boolean value) {
            newAvailable = value;
        }

        private void oldSource(final String value) {
            oldSource = value;
        }

        private void newSource(final String value) {
            newSource = value;
        }

        private void oldFailure(final String value) {
            oldFailure = value;
        }

        private void newFailure(final String value) {
            newFailure = value;
        }

        private void suppressionReasons(
                final EnumSet<MethodBodySuppressionReason> value) {
            suppressionReasons = value;
        }
    }

    private SsaComparisonStatus parseSsaStatus(final String value)
            throws IOException {
        try {
            return SsaComparisonStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid SSA status: " + value, exception);
        }
    }

    private DecompileComparisonStatus parseDecompileStatus(
            final String value) throws IOException {
        try {
            return DecompileComparisonStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Invalid decompile status: " + value, exception);
        }
    }

    private EnumSet<MethodBodySuppressionReason> readSuppressionReasons(
            final JsonParser json) throws IOException {
        if (json.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Invalid suppression reasons");
        }
        final EnumSet<MethodBodySuppressionReason> result =
                EnumSet.noneOf(MethodBodySuppressionReason.class);
        while (json.nextToken() != JsonToken.END_ARRAY) {
            if (json.currentToken() != JsonToken.VALUE_STRING) {
                throw new IOException("Invalid suppression reason");
            }
            try {
                result.add(MethodBodySuppressionReason.valueOf(
                        json.getValueAsString()));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Unknown suppression reason", exception);
            }
        }
        return result;
    }

    private void validateMethodSide(
            final String side,
            final boolean available,
            final String source,
            final String failure) throws IOException {
        if ((available && (blank(source) || failure != null))
                || (!available && (source != null || blank(failure)))) {
            throw new IOException("Invalid " + side
                    + " decompilation payload");
        }
    }

    private void validateSsaComparison(
            final boolean executed,
            final String status,
            final String reason,
            final long elapsedMillis,
            final DecompileComparisonStatus decompileStatus,
            final EnumSet<MethodBodySuppressionReason> reasons)
            throws IOException {
        if (!executed) {
            if (!SSA_NOT_EXECUTED.equals(status)
                    || !SSA_SHORT_CIRCUIT_REASON.equals(reason)
                    || elapsedMillis != 0L
                    || decompileStatus
                    != DecompileComparisonStatus.IDENTICAL
                    || reasons.contains(
                    MethodBodySuppressionReason.SSA_MATCHED)) {
                throw new IOException("Invalid SSA short-circuit evidence");
            }
            return;
        }
        final SsaComparisonStatus parsed = parseSsaStatus(status);
        if (decompileStatus == DecompileComparisonStatus.IDENTICAL
                || reasons.contains(MethodBodySuppressionReason.SSA_MATCHED)
                != (parsed == SsaComparisonStatus.MATCHED)) {
            throw new IOException("Inconsistent executed SSA evidence");
        }
    }

    private void validateJavaComparison(
            final DecompileComparisonStatus decompileStatus,
            final boolean oldAvailable,
            final boolean newAvailable,
            final String oldSource,
            final String newSource,
            final EnumSet<MethodBodySuppressionReason> reasons)
            throws IOException {
        final boolean javaIdentical = decompileStatus
                == DecompileComparisonStatus.IDENTICAL;
        if (reasons.contains(
                MethodBodySuppressionReason.JAVA_TEXT_IDENTICAL)
                != javaIdentical) {
            throw new IOException("Inconsistent suppression reasons");
        }
        if (decompileStatus == DecompileComparisonStatus.UNKNOWN) {
            if (oldAvailable && newAvailable) {
                throw new IOException(
                        "Unknown comparison has two available sides");
            }
            return;
        }
        if (!oldAvailable || !newAvailable) {
            throw new IOException(
                    "Known comparison has unavailable side");
        }
        if (javaIdentical != oldSource.equals(newSource)) {
            throw new IOException("Inconsistent decompiled Java status");
        }
    }

    private boolean blank(final String value) {
        return value == null || value.isBlank();
    }

    private String nullableText(final JsonParser json) throws IOException {
        return json.currentToken() == JsonToken.VALUE_NULL
                ? null : json.getValueAsString();
    }

    private String pairKey(final DependencyChange change) {
        return change.getOldArtifact() + "->" + change.getNewArtifact();
    }

    private String pairKey(final DependencyUpgradeKey key) {
        return key.getOldArtifact() + "->" + key.getNewArtifact();
    }

    private String comparisonKey(
            final String pair,
            final ChangePoint point) {
        return comparisonKey(pair, point.getOwner(), point.getName(),
                point.getOldDescriptor());
    }

    private String comparisonKey(
            final String pair,
            final String owner,
            final String name,
            final String descriptor) {
        return pair + "|" + owner + "|" + name + descriptor;
    }

    /**
     * Parsed cache payload required by the report evidence builder.
     *
     * @param owner internal class owner
     * @param name method name
     * @param descriptor JVM method descriptor
     * @param status decompiled Java comparison status
     * @param oldSource baseline source, nullable
     * @param newSource target source, nullable
     * @param oldFailure baseline failure, nullable
     * @param newFailure target failure, nullable
     */
    private record CachedComparison(
            String owner,
            String name,
            String descriptor,
            DecompileComparisonStatus status,
            String oldSource,
            String newSource,
            String oldFailure,
            String newFailure) {

        String unavailableReason() {
            return "old=" + Objects.requireNonNullElse(
                    oldFailure, "available") + "; new="
                    + Objects.requireNonNullElse(newFailure, "available");
        }
    }
}
