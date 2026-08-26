package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.report.ScriptSafeJson;
import io.github.dependencyanalysis.report.offline.OfflineShardDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight manifest embedded in one tree diff Reactor page. */
final class TreeDiffReportManifest {

    /** Schema name. */
    static final String SCHEMA = "tree-diff-report";

    /** Schema version. */
    static final int VERSION = 1;

    /** Reactor key. */
    private final String reactorKey;

    /** Command metadata. */
    private final TreeDiffReportMetadata metadata;

    /** Module navigation catalog. */
    private final List<ModuleCatalogRecord> modules;

    /** Shards by kind. */
    private final Map<String, List<OfflineShardDescriptor>> shards;

    /** Technical warnings. */
    private final List<String> warnings;

    TreeDiffReportManifest(
            final String key,
            final TreeDiffReportMetadata reportMetadata,
            final List<ModuleCatalogRecord> moduleCatalog,
            final Map<String, List<OfflineShardDescriptor>> descriptors,
            final List<String> technicalWarnings) {
        reactorKey = key;
        metadata = reportMetadata;
        modules = List.copyOf(moduleCatalog);
        shards = Collections.unmodifiableMap(
                new LinkedHashMap<>(descriptors));
        warnings = List.copyOf(technicalWarnings);
    }

    /** @return script-safe JSON */
    String toJson() {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator json = ScriptSafeJson.factory()
                .createGenerator(output)) {
            write(json);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private void write(final JsonGenerator json) throws IOException {
        json.writeStartObject();
        json.writeStringField("schema", SCHEMA);
        json.writeNumberField("version", VERSION);
        json.writeStringField("reactorKey", reactorKey);
        writeSide(json, "baseline", metadata.baseline());
        writeSide(json, "target", metadata.target());
        json.writeStringField("analysisPath",
                displayPath(metadata.analysisPath()));
        json.writeArrayFieldStart("scopes");
        for (String scope : metadata.scopes()) {
            json.writeString(scope);
        }
        json.writeEndArray();
        json.writeNumberField("moduleCount", modules.size());
        json.writeArrayFieldStart("modules");
        for (ModuleCatalogRecord module : modules) {
            json.writeStartObject();
            json.writeNumberField("moduleId", module.moduleId());
            json.writeStringField("moduleKey", module.moduleKey());
            json.writeStringField("baselineCoordinate",
                    module.baselineCoordinate());
            json.writeStringField("targetCoordinate",
                    module.targetCoordinate());
            json.writeStringField("baselineState",
                    module.baselineState().name());
            json.writeStringField("targetState",
                    module.targetState().name());
            json.writeStringField("comparisonStatus",
                    module.comparisonStatus().name());
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
        json.writeArrayFieldStart("technicalWarnings");
        for (String warning : warnings) {
            json.writeString(warning);
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private void writeSide(
            final JsonGenerator json,
            final String field,
            final TreeDiffSideMetadata side) throws IOException {
        json.writeObjectFieldStart(field);
        json.writeStringField("kind", side.kind());
        json.writeStringField("ref", side.ref());
        json.writeStringField("commit", side.commit());
        json.writeBooleanField("dirty", side.dirty());
        json.writeEndObject();
    }

    private String displayPath(final java.nio.file.Path path) {
        return path.toString().isBlank() ? "."
                : path.toString().replace('\\', '/');
    }

    record ModuleCatalogRecord(
            int moduleId,
            String moduleKey,
            String baselineCoordinate,
            String targetCoordinate,
            TreeDiffSideState baselineState,
            TreeDiffSideState targetState,
            TreeDiffComparisonStatus comparisonStatus) {
    }
}
