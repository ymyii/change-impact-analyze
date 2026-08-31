package io.github.dependencyanalysis.tree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Uses cached external grouping for production-sized inputs.
     *
     * @param module module dependency result
     * @param cacheRoot report cache root
     * @return deterministic issues
     * @throws IOException on cache grouping failure
     */
    List<VersionMediationIssue> analyze(
            final ModuleTreeResult module,
            final Path cacheRoot) throws IOException {
        if (!module.isCompleteMediation()) {
            return List.of();
        }
        final List<VersionMediationIssue> result = new ArrayList<>();
        new TreeExternalOccurrenceSorter().group(List.of(module), false,
                cacheRoot, "module-" + module.getPom(), (key, references) -> {
                    final VersionMediationIssue issue = issue(key,
                            references.stream().map(
                                    TreeExternalOccurrenceSorter
                                            .OccurrenceRef::occurrence)
                                    .toList());
                    if (issue != null) {
                        result.add(issue);
                    }
                });
        return List.copyOf(result);
    }

    private VersionMediationIssue issue(
            final DependencyKey key,
            final List<DependencyOccurrence> occurrences) {
        final Set<String> versions = new LinkedHashSet<>();
        for (DependencyOccurrence occurrence : occurrences) {
            VersionPath.from(occurrence).getEvidence().stream()
                    .map(VersionEvidence::getVersion)
                    .forEach(versions::add);
        }
        if (versions.size() < 2) {
            return null;
        }
        final DependencyOccurrence selected = occurrences.stream()
                .filter(DependencyOccurrence::isSelected).findFirst()
                .orElse(occurrences.get(0));
        final Map<String, VersionPath> paths = new java.util.TreeMap<>();
        for (DependencyOccurrence occurrence : occurrences) {
            final VersionPath path = VersionPath.from(occurrence);
            paths.putIfAbsent(path.stableKey(), path);
        }
        return new VersionMediationIssue(key, selected.getSelectedVersion(),
                selected.getEffectiveScope(), new ArrayList<>(paths.values()));
    }
}
