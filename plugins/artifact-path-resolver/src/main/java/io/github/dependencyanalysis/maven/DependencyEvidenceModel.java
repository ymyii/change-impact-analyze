package io.github.dependencyanalysis.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable Dependency Evidence Schema v3 payload. */
final class DependencyEvidenceModel {

    /** Module coordinate. */
    private final ArtifactCoordinates module;

    /** Canonical Module directory. */
    private final Path moduleDirectory;

    /** Selected external dependency projection. */
    private final List<SelectedDependency> dependencies;

    /** Winner-normalized occurrence graph. */
    private final OccurrenceGraph occurrenceGraph;

    /** Selected reactor keys. */
    private final List<String> selectedReactorKeys;

    /** Selected physical artifact bindings. */
    private final List<ResolvedArtifactPath> artifacts;

    DependencyEvidenceModel(
            final ArtifactCoordinates moduleCoordinate,
            final Path directory,
            final List<SelectedDependency> selectedDependencies,
            final OccurrenceGraph graph,
            final List<String> reactorKeys,
            final List<ResolvedArtifactPath> resolvedArtifacts) {
        module = Objects.requireNonNull(moduleCoordinate, "module");
        moduleDirectory = Objects.requireNonNull(
                directory, "moduleDirectory");
        dependencies = immutable(selectedDependencies, "dependencies");
        occurrenceGraph = Objects.requireNonNull(graph, "occurrenceGraph");
        selectedReactorKeys = immutable(
                reactorKeys, "selectedReactorKeys");
        artifacts = immutable(resolvedArtifacts, "artifacts");
    }

    private static <T> List<T> immutable(
            final List<T> values,
            final String name) {
        return Collections.unmodifiableList(new ArrayList<T>(
                Objects.requireNonNull(values, name)));
    }

    ArtifactCoordinates getModule() {
        return module;
    }

    Path getModuleDirectory() {
        return moduleDirectory;
    }

    List<SelectedDependency> getDependencies() {
        return dependencies;
    }

    OccurrenceGraph getOccurrenceGraph() {
        return occurrenceGraph;
    }

    List<String> getSelectedReactorKeys() {
        return selectedReactorKeys;
    }

    List<ResolvedArtifactPath> getArtifacts() {
        return artifacts;
    }

    /** Selected dependency tree node. */
    static final class SelectedDependency {

        /** Winner coordinate. */
        private final ArtifactCoordinates coordinates;

        /** Effective scope. */
        private final String scope;

        /** Selected children. */
        private final List<SelectedDependency> children;

        SelectedDependency(
                final ArtifactCoordinates artifact,
                final String dependencyScope,
                final List<SelectedDependency> childDependencies) {
            coordinates = Objects.requireNonNull(artifact, "coordinates");
            scope = Objects.requireNonNull(dependencyScope, "scope");
            children = immutable(childDependencies, "children");
        }

        ArtifactCoordinates getCoordinates() {
            return coordinates;
        }

        String getScope() {
            return scope;
        }

        List<SelectedDependency> getChildren() {
            return children;
        }
    }

    /** One dependency occurrence. */
    static final class Occurrence {

        /** Stable occurrence id. */
        private final String id;

        /** Winner coordinate. */
        private final ArtifactCoordinates coordinates;

        /** Effective scope, empty only for root. */
        private final String scope;

        /** Module root marker. */
        private final boolean moduleRoot;

        /** Reactor dependency marker. */
        private final boolean reactor;

        Occurrence(
                final String occurrenceId,
                final ArtifactCoordinates artifact,
                final String dependencyScope,
                final boolean root,
                final boolean reactorDependency) {
            id = Objects.requireNonNull(occurrenceId, "id");
            coordinates = Objects.requireNonNull(artifact, "coordinates");
            scope = Objects.requireNonNull(dependencyScope, "scope");
            moduleRoot = root;
            reactor = reactorDependency;
        }

        String getId() {
            return id;
        }

        ArtifactCoordinates getCoordinates() {
            return coordinates;
        }

        String getScope() {
            return scope;
        }

        boolean isModuleRoot() {
            return moduleRoot;
        }

        boolean isReactor() {
            return reactor;
        }
    }

    /** Directed occurrence edge. */
    static final class Edge {

        /** Parent occurrence id. */
        private final String parentId;

        /** Child occurrence id. */
        private final String childId;

        Edge(final String parent, final String child) {
            parentId = Objects.requireNonNull(parent, "parentId");
            childId = Objects.requireNonNull(child, "childId");
        }

        String getParentId() {
            return parentId;
        }

        String getChildId() {
            return childId;
        }
    }

    /** Occurrence graph payload. */
    static final class OccurrenceGraph {

        /** Root occurrence id. */
        private final String rootId;

        /** Occurrence nodes. */
        private final List<Occurrence> occurrences;

        /** Directed edges. */
        private final List<Edge> edges;

        OccurrenceGraph(
                final String root,
                final List<Occurrence> nodes,
                final List<Edge> graphEdges) {
            rootId = Objects.requireNonNull(root, "rootId");
            occurrences = immutable(nodes, "occurrences");
            edges = immutable(graphEdges, "edges");
        }

        String getRootId() {
            return rootId;
        }

        List<Occurrence> getOccurrences() {
            return occurrences;
        }

        List<Edge> getEdges() {
            return edges;
        }
    }
}
