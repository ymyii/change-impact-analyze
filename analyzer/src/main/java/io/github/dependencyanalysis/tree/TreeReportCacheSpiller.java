package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.runtime.ReportCache;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** Writes one Reactor's ordered report evidence to cached JSON Lines. */
final class TreeReportCacheSpiller {

    /**
     * Spills ordered tree and normalized occurrence records.
     *
     * @param result completed reactor result
     * @param cache command report cache
     * @return prefix used for post-publication discard
     */
    String spill(
            final ReactorTreeResult result,
            final ReportCache cache) {
        final String prefix = result.getReactor().getId() + "\n";
        for (ModuleTreeResult module : result.getModules().stream()
                .sorted(Comparator.comparing(value ->
                        value.getPom().toString())).toList()) {
            final String key = prefix + module.getPom();
            spillOccurrences(cache, "tree-record", key, module,
                    true, false);
            spillOccurrences(cache, "occurrence", key, module,
                    false, false);
            spillOccurrences(cache, "reactor-selected", key, module,
                    false, true);
            spillClassConflicts(cache, key, module);
            cache.writeJsonLines("tree-module", key,
                    json -> {
                        json.writeStartObject();
                        json.writeNumberField("schemaVersion",
                                ReportCache.SCHEMA_VERSION);
                        json.writeStringField("reactor",
                                result.getReactor().getId());
                        json.writeStringField("pom",
                                module.getPom().toString());
                        json.writeStringField("coordinate",
                                module.getCoordinate());
                        json.writeNumberField("occurrenceCount",
                                module.getOccurrences().size());
                        json.writeBooleanField("completeMediation",
                                module.isCompleteMediation());
                        json.writeEndObject();
                        return 1;
                    });
        }
        return prefix;
    }

    private void spillClassConflicts(
            final ReportCache cache,
            final String key,
            final ModuleTreeResult module) {
        cache.writeJsonLines("class-conflict-source", key, json -> {
            int count = 0;
            for (TreeClassConflict conflict : module.getClassConflicts()) {
                for (TreeClassConflictCandidate candidate
                        : conflict.candidates()) {
                    json.writeStartObject();
                    json.writeNumberField("schemaVersion", 1);
                    json.writeStringField("module", module.getCoordinate());
                    json.writeStringField("binaryName",
                            conflict.binaryName());
                    json.writeStringField("risk", conflict.risk().name());
                    json.writeStringField("origin", candidate.origin().name());
                    json.writeStringField("source", candidate.source());
                    json.writeStringField("scope", candidate.scope());
                    json.writeStringField("digest", candidate.digest());
                    json.writeStringField("effectiveEntry",
                            candidate.effectiveEntry());
                    json.writeBooleanField("winner",
                            candidate == conflict.winner());
                    json.writeBooleanField("available",
                            candidate.decompiled().isAvailable());
                    if (candidate.decompiled().isAvailable()) {
                        json.writeStringField("sourceCode",
                                candidate.decompiled().getSource());
                    }
                    json.writeEndObject();
                    count++;
                }
            }
            return count;
        });
    }

    private void spillOccurrences(
            final ReportCache cache,
            final String kind,
            final String key,
            final ModuleTreeResult module,
            final boolean ordered,
            final boolean selectedOnly) {
        cache.writeJsonLines(kind, key, json -> {
            int count = 0;
            final List<DependencyOccurrence> values =
                    module.getOccurrences();
            for (int index = 0; index < values.size(); index++) {
                final DependencyOccurrence occurrence = values.get(index);
                if (!selectedOnly || occurrence.isSelected()) {
                    writeOccurrence(json, module, occurrence, index,
                            ordered);
                    count++;
                }
            }
            return count;
        });
    }

    private void writeOccurrence(
            final JsonGenerator json,
            final ModuleTreeResult module,
            final DependencyOccurrence occurrence,
            final int sequence,
            final boolean ordered) throws IOException {
        json.writeStartObject();
        json.writeStringField("module", module.getCoordinate());
        json.writeNumberField("sequence", sequence);
        json.writeBooleanField("orderedTreeRecord", ordered);
        json.writeStringField("dependencyKey",
                occurrence.getKey().toString());
        json.writeStringField("requestedVersion",
                occurrence.getRequestedVersion());
        json.writeStringField("effectiveVersion",
                occurrence.getEffectiveVersion());
        json.writeStringField("selectedVersion",
                occurrence.getSelectedVersion());
        json.writeStringField("scope", occurrence.getEffectiveScope());
        json.writeBooleanField("selected", occurrence.isSelected());
        json.writeBooleanField("reactorModule",
                occurrence.isReactorModule());
        json.writeArrayFieldStart("path");
        for (String node : occurrence.getPath()) {
            json.writeString(node);
        }
        json.writeEndArray();
        json.writeEndObject();
    }

}
