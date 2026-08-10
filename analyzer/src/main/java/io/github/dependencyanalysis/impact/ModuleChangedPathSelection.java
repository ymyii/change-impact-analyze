package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable union of every dependency path reaching a changed artifact. */
public final class ModuleChangedPathSelection {

    /** Requested mode. */
    private final DependencyAnalysisScopeMode requestedMode;

    /** Actual mode after fallback. */
    private final DependencyAnalysisScopeMode actualMode;

    /** Matching changed dependency occurrences. */
    private final List<ModuleDependencyOccurrenceGraph.Occurrence> seeds;

    /** Selected path occurrence identities. */
    private final Set<String> selectedOccurrenceIds;

    /** Selected logical external artifact sources. */
    private final Set<ArtifactCoord> selectedArtifacts;

    /** All complete path evidence, including duplicate logical artifacts. */
    private final List<DependencyPathEvidence> paths;

    /** Structured fallback reason, empty without fallback. */
    private final String fallbackReason;

    private ModuleChangedPathSelection(
            final DependencyAnalysisScopeMode requested,
            final DependencyAnalysisScopeMode actual,
            final List<ModuleDependencyOccurrenceGraph.Occurrence> seedNodes,
            final Set<String> occurrences,
            final Set<ArtifactCoord> artifacts,
            final List<DependencyPathEvidence> pathEvidence,
            final String fallback) {
        requestedMode = Objects.requireNonNull(requested, "requested");
        actualMode = Objects.requireNonNull(actual, "actual");
        seeds = List.copyOf(seedNodes);
        selectedOccurrenceIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(occurrences));
        selectedArtifacts = Collections.unmodifiableSet(
                new LinkedHashSet<>(artifacts));
        paths = List.copyOf(pathEvidence);
        fallbackReason = Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Plans real-IR external artifacts from the occurrence graph.
     *
     * @param graph target Module graph
     * @param changedArtifacts target artifacts that produced ChangePoints
     * @param requested requested mode
     * @return immutable selection or full fallback
     */
    public static ModuleChangedPathSelection plan(
            final ModuleDependencyOccurrenceGraph graph,
            final Set<ArtifactCoord> changedArtifacts,
            final DependencyAnalysisScopeMode requested) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(changedArtifacts, "changedArtifacts");
        Objects.requireNonNull(requested, "requested");
        if (requested == DependencyAnalysisScopeMode.FULL) {
            return full(graph, requested, "");
        }
        final ModuleDependencyOccurrenceGraph.Validation validation =
                graph.validate();
        if (!validation.valid()) {
            return full(graph, requested, validation.reason());
        }
        final List<ModuleDependencyOccurrenceGraph.Occurrence> seeds =
                changedArtifacts.stream()
                        .sorted(Comparator.comparing(ArtifactCoord::toString))
                        .flatMap(value -> graph.occurrencesOf(value).stream())
                        .sorted(Comparator.comparing(
                                ModuleDependencyOccurrenceGraph.Occurrence::id))
                        .toList();
        for (ArtifactCoord artifact : changedArtifacts) {
            if (graph.occurrencesOf(artifact).isEmpty()) {
                return full(graph, requested,
                        "CHANGED_DEPENDENCY_SEED_MISSING:" + artifact);
            }
        }
        final List<DependencyPathEvidence> paths = new ArrayList<>();
        for (ModuleDependencyOccurrenceGraph.Occurrence seed : seeds) {
            final int before = paths.size();
            reversePaths(graph, seed, seed, new ArrayList<>(),
                    new LinkedHashSet<>(), paths);
            if (paths.size() == before) {
                return full(graph, requested,
                        "CHANGED_DEPENDENCY_SEED_UNREACHABLE:"
                                + seed.artifact());
            }
        }
        paths.sort(Comparator.comparing(DependencyPathEvidence::stablePath)
                .thenComparing(value -> value.seed().toString()));
        final Set<String> selectedIds = new LinkedHashSet<>();
        final Set<ArtifactCoord> selected = new LinkedHashSet<>();
        for (DependencyPathEvidence path : paths) {
            for (ModuleDependencyOccurrenceGraph.Occurrence node
                    : path.occurrences()) {
                selectedIds.add(node.id());
                if (!node.reactor()) {
                    selected.add(node.artifact());
                }
            }
        }
        return new ModuleChangedPathSelection(requested, requested, seeds,
                selectedIds, selected, paths, "");
    }

    private static void reversePaths(
            final ModuleDependencyOccurrenceGraph graph,
            final ModuleDependencyOccurrenceGraph.Occurrence seed,
            final ModuleDependencyOccurrenceGraph.Occurrence current,
            final List<ModuleDependencyOccurrenceGraph.Occurrence> reverse,
            final Set<String> active,
            final List<DependencyPathEvidence> result) {
        if (!active.add(current.id())) {
            return;
        }
        if (current.moduleRoot()) {
            final List<ModuleDependencyOccurrenceGraph.Occurrence> path =
                    new ArrayList<>(reverse);
            Collections.reverse(path);
            result.add(new DependencyPathEvidence(seed.artifact(), path));
        } else {
            reverse.add(current);
            for (ModuleDependencyOccurrenceGraph.Occurrence parent
                    : graph.parentsOf(current.id())) {
                reversePaths(graph, seed, parent, reverse, active, result);
            }
            reverse.remove(reverse.size() - 1);
        }
        active.remove(current.id());
    }

    private static ModuleChangedPathSelection full(
            final ModuleDependencyOccurrenceGraph graph,
            final DependencyAnalysisScopeMode requested,
            final String reason) {
        final Set<String> ids = new LinkedHashSet<>();
        final Set<ArtifactCoord> artifacts = new LinkedHashSet<>();
        for (ModuleDependencyOccurrenceGraph.Occurrence node
                : graph.occurrences()) {
            if (!node.moduleRoot()) {
                ids.add(node.id());
                if (!node.reactor()) {
                    artifacts.add(node.artifact());
                }
            }
        }
        return new ModuleChangedPathSelection(requested,
                DependencyAnalysisScopeMode.FULL, List.of(), ids,
                artifacts, List.of(), reason);
    }

    /**
     * Creates a compatibility full-mode selection for a flat artifact list.
     *
     * @param artifacts external artifacts
     * @return full selection
     */
    public static ModuleChangedPathSelection fullArtifacts(
            final List<ArtifactCoord> artifacts) {
        final ArtifactCoord module = new ArtifactCoord(
                "synthetic", "module", "pom", "0");
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                new ArrayList<>();
        final List<ModuleDependencyOccurrenceGraph.Edge> edges =
                new ArrayList<>();
        nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                "module", module, null, true, false));
        int index = 0;
        for (ArtifactCoord artifact : artifacts) {
            final String id = "artifact-" + index++;
            nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                    id, artifact,
                    io.github.dependencyanalysis.dependency.DependencyScope
                            .COMPILE,
                    false, false));
            edges.add(new ModuleDependencyOccurrenceGraph.Edge(
                    "module", id));
        }
        return full(new ModuleDependencyOccurrenceGraph(
                "module", nodes, edges),
                DependencyAnalysisScopeMode.FULL, "");
    }

    /** @return requested scope */
    public DependencyAnalysisScopeMode requestedMode() {
        return requestedMode;
    }

    /** @return actual scope */
    public DependencyAnalysisScopeMode actualMode() {
        return actualMode;
    }

    /** @return matching seed occurrences */
    public List<ModuleDependencyOccurrenceGraph.Occurrence> seeds() {
        return seeds;
    }

    /** @return selected occurrence identities */
    public Set<String> selectedOccurrenceIds() {
        return selectedOccurrenceIds;
    }

    /** @return logical external artifacts that retain real IR */
    public Set<ArtifactCoord> selectedArtifacts() {
        return selectedArtifacts;
    }

    /** @return all complete dependency paths */
    public List<DependencyPathEvidence> paths() {
        return paths;
    }

    /** @return fallback reason when actual mode differs */
    public Optional<String> fallbackReason() {
        return fallbackReason.isEmpty() ? Optional.empty()
                : Optional.of(fallbackReason);
    }

    /**
     * @param artifact external source
     * @return effective body policy
     */
    public DependencyMethodBodyPolicy policyFor(
            final ArtifactCoord artifact) {
        return actualMode == DependencyAnalysisScopeMode.FULL
                || selectedArtifacts.contains(artifact)
                ? DependencyMethodBodyPolicy.REAL_IR
                : DependencyMethodBodyPolicy.NO_OP;
    }

    /** @return path evidence grouped by changed artifact */
    public Map<ArtifactCoord, List<DependencyPathEvidence>> pathsBySeed() {
        final Map<ArtifactCoord, List<DependencyPathEvidence>> result =
                new LinkedHashMap<>();
        for (DependencyPathEvidence path : paths) {
            result.computeIfAbsent(path.seed(), ignored -> new ArrayList<>())
                    .add(path);
        }
        final Map<ArtifactCoord, List<DependencyPathEvidence>> immutable =
                new LinkedHashMap<>();
        result.forEach((key, value) -> immutable.put(key,
                List.copyOf(value)));
        return Collections.unmodifiableMap(immutable);
    }
}
