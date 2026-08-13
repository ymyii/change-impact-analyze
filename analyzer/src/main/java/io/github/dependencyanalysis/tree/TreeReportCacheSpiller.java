package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.runtime.ReportTaskCache;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - Tree cache spill
/** Writes one Reactor's ordered report evidence to task-scoped JSON Lines. */
final class TreeReportCacheSpiller {

    /**
     * Spills ordered tree and normalized occurrence records.
     *
     * @param result completed reactor result
     * @param cache task cache
     * @return prefix used for post-publication discard
     */
    String spill(
            final ReactorTreeResult result,
            final ReportTaskCache cache) {
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
            cache.writeJsonLines("tree-module", key,
                    json -> {
                        json.writeStartObject();
                        json.writeNumberField("schemaVersion",
                                ReportTaskCache.SCHEMA_VERSION);
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

    private void spillOccurrences(
            final ReportTaskCache cache,
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
