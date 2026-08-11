package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Wiki: wiki/features/dependency-evidence-collection.md
// Winner-normalized Module dependency evidence.
/**
 * Module-local selected dependency projection, normalized occurrence
 * topology, and physical artifact bindings.
 */
public final class ModuleDependencyEvidence {

    /** Module coordinate. */
    private final ArtifactCoord module;

    /** Canonical Module directory. */
    private final Path modulePath;

    /** Maven-selected external dependency projection. */
    private final List<DependencyNode> dependencies;

    /** Raw occurrence topology normalized to selected coordinates. */
    private final ModuleDependencyOccurrenceGraph occurrenceGraph;

    /** Selected reactor dependency keys in Maven classpath order. */
    private final List<String> selectedReactorKeys;

    /** Module-local selected external artifact bindings. */
    private final List<ResolvedArtifact> artifacts;

    /** Unique binding lookup. */
    private final Map<ArtifactCoord, ResolvedArtifact> artifactsByCoordinate;

    /**
     * Creates validated Module-local dependency evidence.
     *
     * @param moduleCoordinate Module coordinate
     * @param directory canonical Module directory
     * @param selectedDependencies selected external dependency tree
     * @param normalizedGraph winner-normalized occurrence graph
     * @param reactorKeys selected reactor keys in classpath order
     * @param resolvedArtifacts selected physical artifact bindings
     */
    public ModuleDependencyEvidence(
            final ArtifactCoord moduleCoordinate,
            final Path directory,
            final List<DependencyNode> selectedDependencies,
            final ModuleDependencyOccurrenceGraph normalizedGraph,
            final List<String> reactorKeys,
            final List<ResolvedArtifact> resolvedArtifacts) {
        module = Objects.requireNonNull(moduleCoordinate, "moduleCoordinate");
        modulePath = Objects.requireNonNull(directory, "directory");
        dependencies = List.copyOf(Objects.requireNonNull(
                selectedDependencies, "selectedDependencies"));
        occurrenceGraph = Objects.requireNonNull(
                normalizedGraph, "normalizedGraph");
        selectedReactorKeys = List.copyOf(Objects.requireNonNull(
                reactorKeys, "reactorKeys"));
        artifacts = List.copyOf(Objects.requireNonNull(
                resolvedArtifacts, "resolvedArtifacts"));
        final Map<ArtifactCoord, ResolvedArtifact> indexed =
                new LinkedHashMap<>();
        for (ResolvedArtifact artifact : artifacts) {
            final ResolvedArtifact previous = indexed.putIfAbsent(
                    artifact.getArtifact(), artifact);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate selected artifact binding: module="
                                + module + "; artifact="
                                + artifact.getArtifact());
            }
        }
        artifactsByCoordinate = Collections.unmodifiableMap(indexed);
    }

    /** @return Module coordinate */
    public ArtifactCoord getModule() {
        return module;
    }

    /** @return canonical Module directory */
    public Path getModulePath() {
        return modulePath;
    }

    /** @return selected external dependency projection */
    public List<DependencyNode> getDependencies() {
        return dependencies;
    }

    /** @return winner-normalized occurrence graph */
    public ModuleDependencyOccurrenceGraph getOccurrenceGraph() {
        return occurrenceGraph;
    }

    /** @return selected reactor dependency keys in classpath order */
    public List<String> getSelectedReactorKeys() {
        return selectedReactorKeys;
    }

    /** @return selected external artifact bindings */
    public List<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    /**
     * Resolves one selected physical artifact binding.
     *
     * @param side baseline or target
     * @param artifact selected artifact coordinate
     * @return unique physical binding
     */
    public ResolvedArtifact requireArtifact(
            final String side,
            final ArtifactCoord artifact) {
        final ResolvedArtifact result = artifactsByCoordinate.get(
                Objects.requireNonNull(artifact, "artifact"));
        if (result == null) {
            throw new IllegalStateException(
                    "Selected artifact binding is missing: side=" + side
                            + "; module=" + module
                            + "; manifestDirectory=" + modulePath
                            + "; artifact=" + artifact);
        }
        return result;
    }

    /** @return selected external artifact coordinates */
    public List<ArtifactCoord> getArtifactCoordinates() {
        final List<ArtifactCoord> result = new ArrayList<>();
        artifacts.forEach(value -> result.add(value.getArtifact()));
        return List.copyOf(result);
    }
}
