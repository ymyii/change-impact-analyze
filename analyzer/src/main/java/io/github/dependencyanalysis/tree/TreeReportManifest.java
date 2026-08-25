package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.report.ScriptSafeJson;
import io.github.dependencyanalysis.report.offline.OfflineShardDescriptor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Lightweight browser manifest for one Tree Reactor page.
 *
 * @param schemaVersion browser schema version
 * @param dependencyRows total dependency occurrence rows
 * @param modules lightweight Module catalog
 * @param dependencies lightweight DependencyKey catalog
 * @param scopes exact Scope catalog
 * @param shards payload descriptors by record kind
 */
record TreeReportManifest(
        int schemaVersion,
        int dependencyRows,
        List<ModuleDescriptor> modules,
        List<DependencyDescriptor> dependencies,
        List<ScopeDescriptor> scopes,
        Map<String, List<OfflineShardDescriptor>> shards) {

    /** Serializes script-safe manifest JSON. */
    String toJson() {
        final StringWriter output = new StringWriter();
        try (JsonGenerator json = ScriptSafeJson.factory()
                .createGenerator(output)) {
            json.writeStartObject();
            json.writeNumberField("schemaVersion", schemaVersion);
            json.writeNumberField("dependencyRows", dependencyRows);
            writeModules(json);
            writeDependencies(json);
            writeScopes(json);
            writeShards(json);
            json.writeEndObject();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return output.toString();
    }

    private void writeModules(final JsonGenerator json) throws IOException {
        json.writeArrayFieldStart("modules");
        for (ModuleDescriptor module : modules) {
            json.writeStartObject();
            json.writeNumberField("id", module.id());
            json.writeStringField("label", module.label());
            json.writeStringField("coordinate", module.coordinate());
            json.writeStringField("pom", module.pom());
            json.writeStringField("failure", module.failure());
            range(json, "dependencyRange", module.dependencyStart(),
                    module.dependencyCount());
            range(json, "dependencyCatalogRange",
                    module.dependencyCatalogStart(),
                    module.dependencyCatalogCount());
            json.writeNumberField("internalConflictCount",
                    module.internalConflictCount());
            range(json, "classConflictRange", module.classStart(),
                    module.classCount());
            json.writeNumberField("treeId", module.treeId());
            json.writeNumberField("multiVersionDependencies",
                    module.multiVersionDependencies());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private void writeDependencies(
            final JsonGenerator json) throws IOException {
        json.writeArrayFieldStart("dependencies");
        for (DependencyDescriptor dependency : dependencies) {
            json.writeStartObject();
            json.writeNumberField("id", dependency.id());
            json.writeStringField("value", dependency.value());
            json.writeNumberField("resolvedVersionCount",
                    dependency.resolvedVersionCount());
            json.writeNumberField("uniqueVersionCount",
                    dependency.uniqueVersionCount());
            json.writeNumberField("firstRangeId",
                    dependency.firstRangeId());
            json.writeNumberField("rangeCount", dependency.rangeCount());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private void writeScopes(final JsonGenerator json) throws IOException {
        json.writeArrayFieldStart("scopes");
        for (ScopeDescriptor scope : scopes) {
            json.writeStartObject();
            json.writeStringField("value", scope.value());
            json.writeNumberField("firstRangeId", scope.firstRangeId());
            json.writeNumberField("rangeCount", scope.rangeCount());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private void writeShards(final JsonGenerator json) throws IOException {
        json.writeObjectFieldStart("shards");
        for (Map.Entry<String, List<OfflineShardDescriptor>> entry
                : shards.entrySet()) {
            json.writeArrayFieldStart(entry.getKey());
            for (OfflineShardDescriptor shard : entry.getValue()) {
                json.writeStartObject();
                json.writeNumberField("id", shard.id());
                json.writeStringField("file", shard.file());
                json.writeNumberField("firstId", shard.firstId());
                json.writeNumberField("lastId", shard.lastId());
                json.writeNumberField("records", shard.records());
                json.writeNumberField("bytes", shard.bytes());
                json.writeEndObject();
            }
            json.writeEndArray();
        }
        json.writeEndObject();
    }

    private void range(
            final JsonGenerator json,
            final String name,
            final int start,
            final int count) throws IOException {
        json.writeObjectFieldStart(name);
        json.writeNumberField("start", start);
        json.writeNumberField("count", count);
        json.writeEndObject();
    }

    /**
     * One Module catalog entry.
     *
     * @param id stable Module ID
     * @param label Module display label
     * @param coordinate complete Module coordinate
     * @param pom Module POM path
     * @param failure Module failure text
     * @param dependencyStart first dependency row ID
     * @param dependencyCount dependency row count
     * @param dependencyCatalogStart first Module Dependency catalog ID
     * @param dependencyCatalogCount Module Dependency catalog count
     * @param internalConflictCount internal conflict count
     * @param classStart first class conflict ID
     * @param classCount class conflict count
     * @param treeId dependency tree record ID
     * @param multiVersionDependencies Module multi-version dependency count
     */
    record ModuleDescriptor(
            int id,
            String label,
            String coordinate,
            String pom,
            String failure,
            int dependencyStart,
            int dependencyCount,
            int dependencyCatalogStart,
            int dependencyCatalogCount,
            int internalConflictCount,
            int classStart,
            int classCount,
            int treeId,
            int multiVersionDependencies) {
    }

    /**
     * One DependencyKey catalog entry.
     *
     * @param id stable DependencyKey ID
     * @param value complete DependencyKey
     * @param resolvedVersionCount distinct selected version count
     * @param uniqueVersionCount distinct original/resolved version count
     * @param firstRangeId first exact-filter range ID
     * @param rangeCount exact-filter range count
     */
    record DependencyDescriptor(
            int id,
            String value,
            int resolvedVersionCount,
            int uniqueVersionCount,
            int firstRangeId,
            int rangeCount) {
    }

    /**
     * One exact Scope filter entry.
     *
     * @param value effective Scope
     * @param firstRangeId first exact-filter range ID
     * @param rangeCount exact-filter range count
     */
    record ScopeDescriptor(
            String value,
            int firstRangeId,
            int rangeCount) {
    }
}
