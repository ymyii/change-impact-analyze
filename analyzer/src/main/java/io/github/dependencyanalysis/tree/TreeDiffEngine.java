package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Occurrence-aware dependency tree diff engine. */
final class TreeDiffEngine {

    /**
     * Diffs one ReactorKey pair.
     *
     * @param reactorKey normalized ReactorKey
     * @param baseline baseline side facts
     * @param target target side facts
     * @return deterministic Reactor diff
     */
    TreeDiffReactorResult diff(
            final String reactorKey,
            final TreeDiffSideReactor baseline,
            final TreeDiffSideReactor target) {
        final TreeDiffComparisonStatus reactorStatus =
                TreeDiffComparisonStatus.of(
                        baseline.state(), target.state());
        final boolean requireEvidence = reactorStatus
                == TreeDiffComparisonStatus.COMPARABLE;
        final SideModules baselineModules = modules(
                baseline, requireEvidence);
        final SideModules targetModules = modules(
                target, requireEvidence);
        final Set<String> moduleKeys = new TreeSet<>();
        moduleKeys.addAll(baselineModules.keys());
        moduleKeys.addAll(targetModules.keys());
        final List<TreeDiffModuleResult> modules = new ArrayList<>();
        TreeDiffMetrics metrics = TreeDiffMetrics.ZERO;
        final List<String> issues = new ArrayList<>();
        appendIssue(issues, "baseline", baseline.issue());
        appendIssue(issues, "target", target.issue());
        appendCollectionIssue(issues, "baseline", baseline.collection());
        appendCollectionIssue(issues, "target", target.collection());
        for (String moduleKey : moduleKeys) {
            final SideModule left = baselineModules.resolve(moduleKey);
            final SideModule right = targetModules.resolve(moduleKey);
            final TreeDiffModuleResult module = diffModule(
                    moduleKey, left, right);
            modules.add(module);
            if (module.metrics() != null) {
                metrics = metrics.plus(module.metrics());
            }
            for (String issue : module.issues()) {
                issues.add(moduleKey + ": " + issue);
            }
        }
        return new TreeDiffReactorResult(reactorKey,
                coordinate(baseline.descriptor()),
                coordinate(target.descriptor()),
                baseline.state(), target.state(), reactorStatus,
                modules, metrics, issues);
    }

    private TreeDiffModuleResult diffModule(
            final String moduleKey,
            final SideModule baseline,
            final SideModule target) {
        final TreeDiffComparisonStatus status =
                TreeDiffComparisonStatus.of(
                        baseline.state(), target.state());
        final List<String> issues = new ArrayList<>();
        appendIssue(issues, "baseline", baseline.issue());
        appendIssue(issues, "target", target.issue());
        if (status != TreeDiffComparisonStatus.COMPARABLE) {
            return new TreeDiffModuleResult(moduleKey,
                    baseline.coordinate(), target.coordinate(),
                    baseline.state(), target.state(), status, null,
                    List.of(), baseline.module(), target.module(), issues);
        }
        final NavigableMap<DependencyKey, TreeDiffSideDependency> left =
                aggregate(baseline.module());
        final NavigableMap<DependencyKey, TreeDiffSideDependency> right =
                aggregate(target.module());
        final Set<DependencyKey> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        final List<TreeDependencyDiffRecord> dependencies =
                new ArrayList<>();
        final TreeDiffMetrics.Builder metrics =
                new TreeDiffMetrics.Builder();
        for (DependencyKey key : keys) {
            final TreeDiffSideDependency before = left.get(key);
            final TreeDiffSideDependency after = right.get(key);
            final TreeDependencyBaseChangeType baseType =
                    classify(before, after);
            final boolean scopeChanged = before != null && after != null
                    && !Objects.equals(before.scope(), after.scope());
            metrics.add(baseType, scopeChanged);
            dependencies.add(new TreeDependencyDiffRecord(key,
                    before, after, baseType, scopeChanged,
                    pairPaths(before, after)));
        }
        return new TreeDiffModuleResult(moduleKey,
                baseline.coordinate(), target.coordinate(),
                baseline.state(), target.state(), status, metrics.build(),
                dependencies, baseline.module(), target.module(), issues);
    }

    private NavigableMap<DependencyKey, TreeDiffSideDependency> aggregate(
            final ModuleTreeResult module) {
        final Map<DependencyKey, List<DependencyOccurrence>> grouped =
                new TreeMap<>();
        for (DependencyOccurrence occurrence : module.getOccurrences()) {
            grouped.computeIfAbsent(occurrence.getKey(),
                    ignored -> new ArrayList<>()).add(occurrence);
        }
        final NavigableMap<DependencyKey, TreeDiffSideDependency> result =
                new TreeMap<>();
        for (Map.Entry<DependencyKey, List<DependencyOccurrence>> entry
                : grouped.entrySet()) {
            final DependencyOccurrence first = entry.getValue().get(0);
            final NavigableMap<TreeDiffPathKey,
                    TreeDiffPathOccurrence> paths = new TreeMap<>();
            boolean direct = false;
            for (DependencyOccurrence occurrence : entry.getValue()) {
                final TreeDiffPathOccurrence path = path(module, occurrence);
                paths.put(path.pathKey(), path);
                direct |= path.direct();
            }
            result.put(entry.getKey(), new TreeDiffSideDependency(
                    entry.getKey(), first.getSelectedVersion(),
                    first.getEffectiveScope(), direct, paths));
        }
        return result;
    }

    private TreeDiffPathOccurrence path(
            final ModuleTreeResult module,
            final DependencyOccurrence occurrence) {
        final List<String> coordinates = occurrence.getPath().isEmpty()
                ? List.of(module.getCoordinate(), occurrence.coordinate())
                : occurrence.getPath();
        final List<DependencyKey> identities = new ArrayList<>();
        for (int index = 1; index < coordinates.size(); index++) {
            identities.add(TreeDependencyCoordinate
                    .parse(coordinates.get(index)).key());
        }
        final TreeDiffPathKey key = new TreeDiffPathKey(identities);
        return new TreeDiffPathOccurrence(key,
                displayPath(coordinates), occurrence.getSelectedVersion(),
                occurrence.getManagedFromVersion(),
                occurrence.getEffectiveScope(), identities.size() == 1);
    }

    private String displayPath(final List<String> coordinates) {
        final StringBuilder result = new StringBuilder(coordinates.get(0));
        for (int index = 1; index < coordinates.size(); index++) {
            result.append('\n').append("   ".repeat(index - 1))
                    .append("└─ ").append(coordinates.get(index));
        }
        return result.toString();
    }

    private TreeDependencyBaseChangeType classify(
            final TreeDiffSideDependency baseline,
            final TreeDiffSideDependency target) {
        if (baseline == null) {
            return TreeDependencyBaseChangeType.ADDED;
        }
        if (target == null) {
            return TreeDependencyBaseChangeType.REMOVED;
        }
        if (!Objects.equals(baseline.resolvedVersion(),
                target.resolvedVersion())) {
            return TreeDependencyBaseChangeType.VERSION_CHANGED;
        }
        return TreeDependencyBaseChangeType.RESOLVED_UNCHANGED;
    }

    private List<TreeDiffChainRow> pairPaths(
            final TreeDiffSideDependency baseline,
            final TreeDiffSideDependency target) {
        final NavigableMap<TreeDiffPathKey, TreeDiffPathOccurrence> left =
                baseline == null ? new TreeMap<>() : baseline.paths();
        final NavigableMap<TreeDiffPathKey, TreeDiffPathOccurrence> right =
                target == null ? new TreeMap<>() : target.paths();
        final Set<TreeDiffPathKey> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        final List<TreeDiffChainRow> result = new ArrayList<>();
        for (TreeDiffPathKey key : keys) {
            final TreeDiffPathOccurrence before = left.get(key);
            final TreeDiffPathOccurrence after = right.get(key);
            final TreeChainChangeType type = before != null && after != null
                    ? TreeChainChangeType.UNCHANGED
                    : before != null ? TreeChainChangeType.REMOVED
                    : TreeChainChangeType.ADDED;
            result.add(new TreeDiffChainRow(before, after, type));
        }
        return result;
    }

    private SideModules modules(
            final TreeDiffSideReactor side,
            final boolean requireEvidence) {
        if (side.state() != TreeDiffSideState.PRESENT) {
            return new SideModules(side.state(), Map.of(), Map.of(),
                    side.issue(), requireEvidence);
        }
        final Map<String, String> coordinates = new LinkedHashMap<>();
        for (Path pom : DependencyTreeCollectionSupport.analysisPoms(
                side.inventory(), side.descriptor())) {
            coordinates.put(normalize(pom),
                    side.inventory().coordinateOf(pom));
        }
        final Map<String, ModuleTreeResult> collected = new LinkedHashMap<>();
        if (side.collection() != null) {
            for (ModuleTreeResult module : side.collection().getModules()) {
                collected.put(normalize(module.getPom()), module);
            }
        }
        return new SideModules(TreeDiffSideState.PRESENT,
                coordinates, collected, side.issue(), requireEvidence);
    }

    private String normalize(final Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private String coordinate(final ReactorDescriptor descriptor) {
        return descriptor == null ? "" : descriptor.getCoordinate();
    }

    private void appendCollectionIssue(
            final List<String> issues,
            final String side,
            final ReactorTreeResult collection) {
        if (collection != null
                && collection.getStatus() != ReactorStatus.SUCCESS) {
            appendIssue(issues, side, collection.getReason());
        }
    }

    private void appendIssue(
            final List<String> issues,
            final String side,
            final String issue) {
        if (issue != null && !issue.isBlank()) {
            issues.add(side + ": " + issue);
        }
    }

    private record SideModules(
            TreeDiffSideState reactorState,
            Map<String, String> coordinates,
            Map<String, ModuleTreeResult> modules,
            String issue,
            boolean requireEvidence) {

        Set<String> keys() {
            return coordinates.keySet();
        }

        SideModule resolve(final String key) {
            if (reactorState == TreeDiffSideState.UNAVAILABLE) {
                return new SideModule(TreeDiffSideState.UNAVAILABLE,
                        "", null, issue);
            }
            if (reactorState == TreeDiffSideState.ABSENT
                    || !coordinates.containsKey(key)) {
                return new SideModule(TreeDiffSideState.ABSENT,
                        "", null, "");
            }
            final ModuleTreeResult module = modules.get(key);
            if (!requireEvidence) {
                return new SideModule(TreeDiffSideState.PRESENT,
                        coordinates.get(key), module, "");
            }
            if (module == null) {
                return new SideModule(TreeDiffSideState.UNAVAILABLE,
                        coordinates.get(key), null,
                        "Dependency collection result is unavailable");
            }
            if (!module.getFailure().isBlank()
                    || !module.isCompleteMediation()) {
                final String reason = !module.getFailure().isBlank()
                        ? module.getFailure()
                        : "Verbose dependency mediation is incomplete";
                return new SideModule(TreeDiffSideState.UNAVAILABLE,
                        coordinates.get(key), module, reason);
            }
            return new SideModule(TreeDiffSideState.PRESENT,
                    coordinates.get(key), module, "");
        }
    }

    private record SideModule(
            TreeDiffSideState state,
            String coordinate,
            ModuleTreeResult module,
            String issue) {
    }
}
