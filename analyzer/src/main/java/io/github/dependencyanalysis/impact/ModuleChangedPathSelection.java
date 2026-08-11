package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable union of every dependency path reaching a changed artifact. */
public final class ModuleChangedPathSelection {

    /** Requested mode. */
    private final DependencyAnalysisScopeMode requestedMode;

    /** Actual mode after fallback. */
    private final DependencyAnalysisScopeMode actualMode;

    /** Selected logical external artifact sources. */
    private final Set<ArtifactCoord> selectedArtifacts;

    /** All selected target bindings allowed by this policy. */
    private final Set<ArtifactCoord> availableArtifacts;

    /** All complete path evidence, including duplicate logical artifacts. */
    private final List<DependencyPathEvidence> paths;

    /** Structured fallback reason, empty without fallback. */
    private final String fallbackReason;

    private ModuleChangedPathSelection(
            final DependencyAnalysisScopeMode requested,
            final DependencyAnalysisScopeMode actual,
            final Set<ArtifactCoord> available,
            final Set<ArtifactCoord> artifacts,
            final List<DependencyPathEvidence> pathEvidence,
            final String fallback) {
        requestedMode = Objects.requireNonNull(requested, "requested");
        actualMode = Objects.requireNonNull(actual, "actual");
        availableArtifacts = Collections.unmodifiableSet(
                new LinkedHashSet<>(available));
        selectedArtifacts = Collections.unmodifiableSet(
                new LinkedHashSet<>(artifacts));
        paths = List.copyOf(pathEvidence);
        fallbackReason = Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Plans real-IR external artifacts from the occurrence graph.
     *
     * @param evidence target merged dependency evidence
     * @param changedArtifacts target artifacts that produced ChangePoints
     * @param requested requested mode
     * @return immutable selection or full fallback
     */
    public static ModuleChangedPathSelection plan(
            final ModuleDependencyEvidence evidence,
            final Set<ArtifactCoord> changedArtifacts,
            final DependencyAnalysisScopeMode requested) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(changedArtifacts, "changedArtifacts");
        Objects.requireNonNull(requested, "requested");
        final ModuleDependencyOccurrenceGraph graph =
                evidence.getOccurrenceGraph();
        final Set<ArtifactCoord> available = new LinkedHashSet<>(
                evidence.getArtifactCoordinates());
        if (requested == DependencyAnalysisScopeMode.FULL) {
            return full(available, requested, "");
        }
        final ModuleDependencyOccurrenceGraph.Validation validation =
                graph.validate();
        if (!validation.valid()) {
            return full(available, requested, validation.reason());
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
                return full(available, requested,
                        "CHANGED_DEPENDENCY_SEED_MISSING:" + artifact);
            }
        }
        final List<DependencyPathEvidence> paths = new ArrayList<>();
        for (ModuleDependencyOccurrenceGraph.Occurrence seed : seeds) {
            final int before = paths.size();
            reversePaths(graph, seed, seed, new ArrayList<>(),
                    new LinkedHashSet<>(), paths);
            if (paths.size() == before) {
                return full(available, requested,
                        "CHANGED_DEPENDENCY_SEED_UNREACHABLE:"
                                + seed.artifact());
            }
        }
        paths.sort(Comparator.comparing(DependencyPathEvidence::stablePath)
                .thenComparing(value -> value.seed().toString()));
        final Set<ArtifactCoord> selected = new LinkedHashSet<>();
        for (DependencyPathEvidence path : paths) {
            for (ModuleDependencyOccurrenceGraph.Occurrence node
                    : path.occurrences()) {
                if (!node.reactor() && !node.moduleRoot()) {
                    if (!available.contains(node.artifact())) {
                        throw new IllegalStateException(
                                "Normalized path artifact has no selected "
                                        + "binding: module="
                                        + evidence.getModule()
                                        + "; artifact=" + node.artifact());
                    }
                    selected.add(node.artifact());
                }
            }
        }
        return new ModuleChangedPathSelection(requested, requested,
                available, selected, paths, "");
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
            final Set<ArtifactCoord> artifacts,
            final DependencyAnalysisScopeMode requested,
            final String reason) {
        return new ModuleChangedPathSelection(requested,
                DependencyAnalysisScopeMode.FULL, artifacts, artifacts,
                List.of(), reason);
    }

    /**
     * Creates a compatibility full-mode selection for a flat artifact list.
     *
     * @param artifacts external artifacts
     * @return full selection
     */
    public static ModuleChangedPathSelection fullArtifacts(
            final List<ArtifactCoord> artifacts) {
        return full(new LinkedHashSet<>(artifacts),
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
        if (!availableArtifacts.contains(artifact)) {
            throw new IllegalArgumentException(
                    "Artifact is not a selected target binding: "
                            + artifact);
        }
        return actualMode == DependencyAnalysisScopeMode.FULL
                || selectedArtifacts.contains(artifact)
                ? DependencyMethodBodyPolicy.REAL_IR
                : DependencyMethodBodyPolicy.NO_OP;
    }

}
