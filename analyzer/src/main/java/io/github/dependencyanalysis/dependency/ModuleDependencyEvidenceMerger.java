package io.github.dependencyanalysis.dependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/features/dependency-tree-extraction.md - GraphML merge invariant
/**
 * Merges selected dependency coordinates with occurrence-preserving topology.
 */
public final class ModuleDependencyEvidenceMerger {

    private ModuleDependencyEvidenceMerger() {
    }

    /**
     * Merges ordinary GraphML, verbose GraphML, and Artifact Path JSON.
     *
     * @param selectedTrees ordinary GraphML trees
     * @param occurrenceTrees verbose GraphML trees
     * @param manifests module-local Artifact Path JSON manifests
     * @param reactorCoordinates reactor Module coordinates
     * @return deterministic merged Module evidence
     * @throws DependencyAnalysisException inconsistent evidence
     */
    public static List<ModuleDependencyEvidence> merge(
            final List<ModuleDependencyTree> selectedTrees,
            final List<ModuleDependencyTree> occurrenceTrees,
            final List<ResolvedArtifactManifest> manifests,
            final Set<ArtifactCoord> reactorCoordinates)
            throws DependencyAnalysisException {
        Objects.requireNonNull(selectedTrees, "selectedTrees");
        Objects.requireNonNull(occurrenceTrees, "occurrenceTrees");
        Objects.requireNonNull(manifests, "manifests");
        Objects.requireNonNull(reactorCoordinates, "reactorCoordinates");
        final Map<Path, ModuleDependencyTree> selected = treesByDirectory(
                "ordinary GraphML", selectedTrees);
        final Map<Path, ModuleDependencyTree> occurrences = treesByDirectory(
                "verbose GraphML", occurrenceTrees);
        final Map<Path, ResolvedArtifactManifest> bindings =
                manifestsByDirectory(manifests);
        requireSameDirectories(selected, occurrences, bindings);
        final Set<ArtifactCoord> effectiveReactorCoordinates =
                new LinkedHashSet<>(reactorCoordinates);
        selected.values().forEach(value ->
                effectiveReactorCoordinates.add(value.getModule()));
        final List<ModuleDependencyEvidence> result = new ArrayList<>();
        final Set<String> moduleKeys = new LinkedHashSet<>();
        for (Map.Entry<Path, ModuleDependencyTree> entry
                : selected.entrySet()) {
            final ModuleDependencyEvidence evidence = mergeModule(
                    entry.getKey(), entry.getValue(),
                    occurrences.get(entry.getKey()),
                    bindings.get(entry.getKey()),
                    effectiveReactorCoordinates);
            if (!moduleKeys.add(evidence.getModule().diffKey())) {
                throw new DependencyAnalysisException(
                        "Duplicate selected Module identity: "
                                + evidence.getModule().diffKey());
            }
            result.add(evidence);
        }
        result.sort(Comparator.comparing(value ->
                value.getModulePath().toString()));
        return List.copyOf(result);
    }

    private static ModuleDependencyEvidence mergeModule(
            final Path directory,
            final ModuleDependencyTree selected,
            final ModuleDependencyTree occurrences,
            final ResolvedArtifactManifest manifest,
            final Set<ArtifactCoord> reactorCoordinates)
            throws DependencyAnalysisException {
        if (!selected.getModule().equals(occurrences.getModule())) {
            throw new DependencyAnalysisException(
                    "Ordinary and verbose GraphML Module differ: directory="
                            + directory + "; ordinary="
                            + selected.getModule() + "; verbose="
                            + occurrences.getModule());
        }
        requireValidGraph("ordinary GraphML", selected.getModule(),
                selected.getOccurrenceGraph());
        requireRoot("verbose GraphML", occurrences.getModule(),
                occurrences.getOccurrenceGraph());
        final Map<String, ArtifactCoord> winners = winnerIndex(selected);
        final List<String> selectedReactors = selectedReactorKeys(
                selected.getOccurrenceGraph(), reactorCoordinates,
                selected.getModule().diffKey());
        final Set<String> selectedReactorKeySet =
                new LinkedHashSet<>(selectedReactors);
        final ModuleDependencyOccurrenceGraph normalized = normalize(
                occurrences, winners, selectedReactorKeySet);
        final List<DependencyNode> externalDependencies = externalNodes(
                selected.getDependencies(), selectedReactorKeySet);
        validateBindings(selected.getModule(), externalDependencies,
                manifest.getArtifacts());
        return new ModuleDependencyEvidence(selected.getModule(), directory,
                externalDependencies, normalized, selectedReactors,
                manifest.getArtifacts());
    }

    private static Map<String, ArtifactCoord> winnerIndex(
            final ModuleDependencyTree selected)
            throws DependencyAnalysisException {
        final Map<String, ArtifactCoord> winners = new LinkedHashMap<>();
        for (ModuleDependencyOccurrenceGraph.Occurrence occurrence
                : selected.getOccurrenceGraph().occurrences()) {
            if (occurrence.moduleRoot()) {
                continue;
            }
            final String key = occurrence.artifact().diffKey();
            final ArtifactCoord previous = winners.putIfAbsent(
                    key, occurrence.artifact());
            if (previous != null && !previous.equals(occurrence.artifact())) {
                throw new DependencyAnalysisException(
                        "Ordinary GraphML selected winner is not unique: "
                                + "module=" + selected.getModule()
                                + "; key=" + key + "; coordinates="
                                + List.of(previous, occurrence.artifact()));
            }
        }
        return winners;
    }

    private static ModuleDependencyOccurrenceGraph normalize(
            final ModuleDependencyTree source,
            final Map<String, ArtifactCoord> winners,
            final Set<String> reactorKeys)
            throws DependencyAnalysisException {
        final List<ModuleDependencyOccurrenceGraph.Occurrence> normalized =
                new ArrayList<>();
        final Set<String> matched = new LinkedHashSet<>();
        for (ModuleDependencyOccurrenceGraph.Occurrence occurrence
                : source.getOccurrenceGraph().occurrences()) {
            final ArtifactCoord artifact;
            if (occurrence.moduleRoot()) {
                artifact = source.getModule();
            } else {
                final String key = occurrence.artifact().diffKey();
                artifact = winners.get(key);
                if (artifact == null) {
                    throw new DependencyAnalysisException(
                            "Verbose GraphML occurrence has no selected "
                                    + "winner: module=" + source.getModule()
                                    + "; occurrence=" + occurrence.id()
                                    + "; key=" + key);
                }
                matched.add(key);
            }
            normalized.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                    occurrence.id(), artifact, occurrence.scope(),
                    occurrence.moduleRoot(), !occurrence.moduleRoot()
                    && reactorKeys.contains(artifact.diffKey())));
        }
        if (!matched.containsAll(winners.keySet())) {
            final Set<String> missing = new LinkedHashSet<>(winners.keySet());
            missing.removeAll(matched);
            throw new DependencyAnalysisException(
                    "Verbose GraphML is missing selected winners: module="
                            + source.getModule() + "; keys=" + missing);
        }
        final ModuleDependencyOccurrenceGraph graph =
                new ModuleDependencyOccurrenceGraph(
                        source.getOccurrenceGraph().root().id(), normalized,
                        source.getOccurrenceGraph().edges());
        requireRoot("normalized occurrence GraphML", source.getModule(),
                graph);
        return graph;
    }

    private static List<DependencyNode> externalNodes(
            final List<DependencyNode> nodes,
            final Set<String> reactorKeys) {
        final List<DependencyNode> result = new ArrayList<>();
        for (DependencyNode node : nodes) {
            final List<DependencyNode> children = externalNodes(
                    node.getChildren(), reactorKeys);
            if (reactorKeys.contains(node.getArtifact().diffKey())) {
                result.addAll(children);
            } else {
                result.add(new DependencyNode(node.getArtifact(),
                        node.getScope(), children));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> selectedReactorKeys(
            final ModuleDependencyOccurrenceGraph graph,
            final Set<ArtifactCoord> reactorCoordinates,
            final String currentModule) {
        final Set<String> result = new LinkedHashSet<>();
        collectSelectedReactors(graph, graph.root(), reactorCoordinates,
                currentModule, result, new LinkedHashSet<>());
        return List.copyOf(result);
    }

    private static void collectSelectedReactors(
            final ModuleDependencyOccurrenceGraph graph,
            final ModuleDependencyOccurrenceGraph.Occurrence current,
            final Set<ArtifactCoord> reactorCoordinates,
            final String currentModule,
            final Set<String> result,
            final Set<String> active) {
        if (!active.add(current.id())) {
            return;
        }
        if (!current.moduleRoot()) {
            final String key = current.artifact().diffKey();
            if (!key.equals(currentModule) && (current.reactor()
                    || reactorCoordinates.contains(current.artifact()))) {
                result.add(key);
            }
        }
        for (ModuleDependencyOccurrenceGraph.Occurrence child
                : graph.childrenOf(current.id())) {
            collectSelectedReactors(graph, child, reactorCoordinates,
                    currentModule, result, active);
        }
        active.remove(current.id());
    }

    private static void validateBindings(
            final ArtifactCoord module,
            final List<DependencyNode> dependencies,
            final List<ResolvedArtifact> artifacts)
            throws DependencyAnalysisException {
        final Set<ArtifactCoord> expected = new LinkedHashSet<>();
        collectArtifacts(dependencies, expected);
        final Set<ArtifactCoord> actual = new LinkedHashSet<>();
        for (ResolvedArtifact artifact : artifacts) {
            if (!actual.add(artifact.getArtifact())) {
                throw new DependencyAnalysisException(
                        "Selected artifact binding is not unique: module="
                                + module + "; artifact="
                                + artifact.getArtifact());
            }
        }
        if (!expected.equals(actual)) {
            final Set<ArtifactCoord> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            final Set<ArtifactCoord> unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(expected);
            throw new DependencyAnalysisException(
                    "Selected GraphML and JSON bindings differ: module="
                            + module + "; missing=" + missing
                            + "; unexpected=" + unexpected);
        }
    }

    private static void collectArtifacts(
            final List<DependencyNode> nodes,
            final Set<ArtifactCoord> result) {
        for (DependencyNode node : nodes) {
            result.add(node.getArtifact());
            collectArtifacts(node.getChildren(), result);
        }
    }

    private static void requireValidGraph(
            final String source,
            final ArtifactCoord module,
            final ModuleDependencyOccurrenceGraph graph)
            throws DependencyAnalysisException {
        final ModuleDependencyOccurrenceGraph.Validation validation =
                graph.validate();
        if (!validation.valid()) {
            throw new DependencyAnalysisException(
                    source + " graph is invalid: module=" + module
                            + "; reason=" + validation.reason());
        }
        requireRoot(source, module, graph);
    }

    private static void requireRoot(
            final String source,
            final ArtifactCoord module,
            final ModuleDependencyOccurrenceGraph graph)
            throws DependencyAnalysisException {
        if (graph.root() == null || !graph.root().moduleRoot()
                || !module.equals(graph.root().artifact())) {
            throw new DependencyAnalysisException(
                    source + " root differs from Module: module=" + module
                            + "; root=" + (graph.root() == null ? "null"
                            : graph.root().artifact()));
        }
    }

    private static Map<Path, ModuleDependencyTree> treesByDirectory(
            final String source,
            final List<ModuleDependencyTree> trees)
            throws DependencyAnalysisException {
        final Map<Path, ModuleDependencyTree> result = new LinkedHashMap<>();
        for (ModuleDependencyTree tree : trees) {
            final Path directory = canonicalDirectory(tree.getModulePath());
            if (result.putIfAbsent(directory, tree) != null) {
                throw new DependencyAnalysisException(
                        "Duplicate " + source + " Module directory: "
                                + directory);
            }
        }
        return result;
    }

    private static Map<Path, ResolvedArtifactManifest>
            manifestsByDirectory(
                    final List<ResolvedArtifactManifest> manifests)
                    throws DependencyAnalysisException {
        final Map<Path, ResolvedArtifactManifest> result =
                new LinkedHashMap<>();
        for (ResolvedArtifactManifest manifest : manifests) {
            final Path directory = canonicalDirectory(
                    manifest.getDirectory());
            if (result.putIfAbsent(directory, manifest) != null) {
                throw new DependencyAnalysisException(
                        "Duplicate JSON Module directory: " + directory);
            }
        }
        return result;
    }

    private static void requireSameDirectories(
            final Map<Path, ModuleDependencyTree> selected,
            final Map<Path, ModuleDependencyTree> occurrences,
            final Map<Path, ResolvedArtifactManifest> bindings)
            throws DependencyAnalysisException {
        if (!selected.keySet().equals(occurrences.keySet())
                || !selected.keySet().equals(bindings.keySet())) {
            throw new DependencyAnalysisException(
                    "Dependency evidence Module sets differ: ordinary="
                            + selected.keySet() + "; verbose="
                            + occurrences.keySet() + "; json="
                            + bindings.keySet());
        }
    }

    private static Path canonicalDirectory(final Path directory)
            throws DependencyAnalysisException {
        try {
            return directory.toRealPath();
        } catch (IOException exception) {
            throw new DependencyAnalysisException(
                    "Module directory is unavailable: " + directory,
                    exception);
        }
    }
}
