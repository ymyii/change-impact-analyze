package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.report.offline.OfflineShardDescriptor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Immutable manifest for one Module's Affected Paths data shards.
 *
 * @param schemaVersion browser data schema
 * @param impactRows Impact relation count
 * @param structuralRows Structural relation count
 * @param sources sorted changed-member source catalog
 * @param shards descriptors grouped by entity kind
 */
record AffectedPathReportManifest(
        int schemaVersion,
        long impactRows,
        long structuralRows,
        List<SourceDescriptor> sources,
        Map<String, List<OfflineShardDescriptor>> shards) {

    /** Serializes the browser manifest. */
    String toJson() {
        final StringWriter output = new StringWriter();
        try (JsonGenerator json = ScriptSafeJson.factory()
                .createGenerator(output)) {
            json.writeStartObject();
            json.writeNumberField("schemaVersion", schemaVersion);
            json.writeObjectFieldStart("rowRanges");
            range(json, "impact", 0L, impactRows);
            range(json, "structural", impactRows, structuralRows);
            range(json, "all", 0L, impactRows + structuralRows);
            json.writeEndObject();
            json.writeArrayFieldStart("sources");
            for (SourceDescriptor source : sources) {
                json.writeStartObject();
                json.writeStringField("source", source.source());
                json.writeNumberField("firstId", source.firstId());
                json.writeNumberField("count", source.count());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeObjectFieldStart("shards");
            for (Map.Entry<String, List<OfflineShardDescriptor>> entry
                    : shards.entrySet()) {
                json.writeArrayFieldStart(entry.getKey());
                for (OfflineShardDescriptor descriptor : entry.getValue()) {
                    json.writeStartObject();
                    json.writeNumberField("id", descriptor.id());
                    json.writeStringField("file", descriptor.file());
                    json.writeNumberField("firstId", descriptor.firstId());
                    json.writeNumberField("lastId", descriptor.lastId());
                    json.writeNumberField("records", descriptor.records());
                    json.writeNumberField("bytes", descriptor.bytes());
                    json.writeEndObject();
                }
                json.writeEndArray();
            }
            json.writeEndObject();
            json.writeEndObject();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return output.toString();
    }

    /**
     * One source catalog entry into the lightweight source-range index.
     *
     * @param source target groupId:artifactId key
     * @param firstId first source-range record ID
     * @param count source-range record count
     */
    record SourceDescriptor(String source, int firstId, int count) {
    }

    private void range(
            final JsonGenerator json,
            final String name,
            final long start,
            final long count) throws IOException {
        json.writeObjectFieldStart(name);
        json.writeNumberField("start", start);
        json.writeNumberField("count", count);
        json.writeEndObject();
    }

}
