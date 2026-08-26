package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.runtime.ReportCache;

import java.util.Comparator;

/** Writes compact side projections into one command-owned ReportCache. */
final class TreeDiffReportCacheSpiller {

    /**
     * Spills one side of one Reactor.
     *
     * @param sideName baseline or target
     * @param reactorKey ReactorKey
     * @param collection dependency collection, nullable
     * @param cache command cache
     * @return stable key prefix for post-publication release
     */
    String spill(
            final String sideName,
            final String reactorKey,
            final ReactorTreeResult collection,
            final ReportCache cache) {
        final String prefix = sideName + "\n" + reactorKey + "\n";
        if (collection == null) {
            return prefix;
        }
        for (ModuleTreeResult module : collection.getModules().stream()
                .sorted(Comparator.comparing(value -> value.getPom()
                        .toString())).toList()) {
            final String key = prefix + module.getPom();
            cache.writeJsonLines("tree-diff-side", key, json -> {
                int count = 0;
                for (DependencyOccurrence occurrence
                        : module.getOccurrences()) {
                    json.writeStartObject();
                    json.writeStringField("side", sideName);
                    json.writeStringField("reactorKey", reactorKey);
                    json.writeStringField("moduleKey",
                            module.getPom().toString().replace('\\', '/'));
                    json.writeStringField("dependencyKey",
                            occurrence.getKey().toString());
                    json.writeStringField("resolvedVersion",
                            occurrence.getSelectedVersion());
                    json.writeStringField("scope",
                            occurrence.getEffectiveScope());
                    json.writeStringField("managedFromVersion",
                            occurrence.getManagedFromVersion());
                    json.writeArrayFieldStart("path");
                    for (String node : occurrence.getPath()) {
                        json.writeString(node);
                    }
                    json.writeEndArray();
                    json.writeEndObject();
                    count++;
                }
                return count;
            });
        }
        return prefix;
    }
}
