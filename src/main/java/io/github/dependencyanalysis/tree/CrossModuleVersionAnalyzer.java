package io.github.dependencyanalysis.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes selected-version divergence across modules. */
public final class CrossModuleVersionAnalyzer {

    /**
     * Analyzes one reactor.
     *
     * @param reactor reactor result
     * @return deterministic divergence issues
     */
    public List<CrossModuleVersionIssue> analyze(
            final ReactorTreeResult reactor) {
        final Map<DependencyKey,
                List<ModuleOccurrence>> grouped =
                new java.util.TreeMap<>();
        for (ModuleTreeResult module
                : reactor.getModules()) {
            for (DependencyOccurrence occurrence
                    : module.getOccurrences()) {
                grouped.computeIfAbsent(
                        occurrence.getKey(),
                        ignored -> new ArrayList<>())
                        .add(new ModuleOccurrence(
                                module.getCoordinate(),
                                occurrence));
            }
        }
        final List<CrossModuleVersionIssue> result =
                new ArrayList<>();
        for (Map.Entry<DependencyKey,
                List<ModuleOccurrence>> entry
                : grouped.entrySet()) {
            final Set<String> versions =
                    new LinkedHashSet<>();
            for (ModuleOccurrence occurrence
                    : entry.getValue()) {
                if (occurrence.occurrence.isSelected()) {
                    versions.add(occurrence.occurrence
                            .getSelectedVersion());
                }
            }
            if (versions.size() < 2) {
                continue;
            }
            final Map<String, Long> counts =
                    new LinkedHashMap<>();
            final Map<String, ModuleOccurrence> samples =
                    new LinkedHashMap<>();
            for (ModuleOccurrence occurrence
                    : entry.getValue()) {
                if (!occurrence.occurrence.isSelected()) {
                    continue;
                }
                final String id = occurrence.module
                        + "|" + occurrence.occurrence
                        .getSelectedVersion()
                        + "|" + occurrence.occurrence
                        .getEffectiveScope();
                counts.merge(id, 1L, Long::sum);
                samples.putIfAbsent(id, occurrence);
            }
            final List<CrossModuleVersion> values =
                    new ArrayList<>();
            for (Map.Entry<String, Long> count
                    : counts.entrySet()) {
                final ModuleOccurrence sample =
                        samples.get(count.getKey());
                final Map<String, VersionPath> paths =
                        new java.util.TreeMap<>();
                for (ModuleOccurrence candidate
                        : entry.getValue()) {
                    if (!sample.module.equals(
                            candidate.module)) {
                        continue;
                    }
                    final VersionPath path = VersionPath.from(
                            candidate.occurrence);
                    paths.putIfAbsent(path.stableKey(), path);
                }
                values.add(new CrossModuleVersion(
                        sample.module,
                        sample.occurrence
                                .getSelectedVersion(),
                        sample.occurrence
                                .getEffectiveScope(),
                        count.getValue(),
                        new ArrayList<>(paths.values())));
            }
            values.sort(java.util.Comparator
                    .comparing(CrossModuleVersion
                            ::getVersion)
                    .thenComparing(CrossModuleVersion
                            ::getModule));
            result.add(new CrossModuleVersionIssue(
                    entry.getKey(), values));
        }
        return result;
    }

    /** Module and occurrence pair. */
    private static final class ModuleOccurrence {

        /** Module coordinate. */
        private final String module;

        /** Occurrence. */
        private final DependencyOccurrence occurrence;

        private ModuleOccurrence(
                final String moduleCoordinate,
                final DependencyOccurrence value) {
            module = moduleCoordinate;
            occurrence = value;
        }
    }
}
