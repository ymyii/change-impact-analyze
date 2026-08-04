package io.github.dependencyanalysis.tree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/features/repository-dependency-tree-report.md - conflict analyzer
/** Computes module-local version mediation issues. */
public final class ModuleVersionAnalyzer {

    /**
     * Analyzes one module.
     *
     * @param module module dependency result
     * @return deterministic issues
     */
    public List<VersionMediationIssue> analyze(
            final ModuleTreeResult module) {
        if (!module.isCompleteMediation()) {
            return List.of();
        }
        final Map<DependencyKey,
                List<DependencyOccurrence>> grouped =
                new java.util.TreeMap<>();
        for (DependencyOccurrence occurrence
                : module.getOccurrences()) {
            grouped.computeIfAbsent(
                    occurrence.getKey(),
                    ignored -> new ArrayList<>())
                    .add(occurrence);
        }
        final List<VersionMediationIssue> result =
                new ArrayList<>();
        for (Map.Entry<DependencyKey,
                List<DependencyOccurrence>> entry
                : grouped.entrySet()) {
            final Set<String> versions =
                    new LinkedHashSet<>();
            for (DependencyOccurrence occurrence
                    : entry.getValue()) {
                VersionPath.from(occurrence)
                        .getEvidence().stream()
                        .map(VersionEvidence::getVersion)
                        .forEach(versions::add);
            }
            if (versions.size() < 2) {
                continue;
            }
            final DependencyOccurrence selected =
                    entry.getValue().stream()
                            .filter(DependencyOccurrence
                                    ::isSelected)
                            .findFirst()
                            .orElse(entry.getValue()
                                    .get(0));
            final Map<String, VersionPath> paths =
                    new java.util.TreeMap<>();
            for (DependencyOccurrence occurrence
                    : entry.getValue()) {
                final VersionPath path =
                        VersionPath.from(occurrence);
                paths.putIfAbsent(path.stableKey(), path);
            }
            result.add(new VersionMediationIssue(
                    entry.getKey(),
                    selected.getSelectedVersion(),
                    selected.getEffectiveScope(),
                    new ArrayList<>(paths.values())));
        }
        return result;
    }
}
