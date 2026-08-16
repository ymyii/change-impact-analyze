package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonGenerator;

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
 * @param shards descriptors grouped by entity kind
 */
record AffectedPathReportManifest(
        int schemaVersion,
        long impactRows,
        long structuralRows,
        Map<String, List<ShardDescriptor>> shards) {

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
            json.writeObjectFieldStart("shards");
            for (Map.Entry<String, List<ShardDescriptor>> entry
                    : shards.entrySet()) {
                json.writeArrayFieldStart(entry.getKey());
                for (ShardDescriptor descriptor : entry.getValue()) {
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

    /**
     * One deterministic shard reference.
     *
     * @param id kind-local shard ID
     * @param file page-relative local file
     * @param firstId first record ID
     * @param lastId last record ID
     * @param records record count
     * @param bytes UTF-8 file bytes
     */
    record ShardDescriptor(
            int id,
            String file,
            int firstId,
            int lastId,
            int records,
            long bytes) {
    }
}
