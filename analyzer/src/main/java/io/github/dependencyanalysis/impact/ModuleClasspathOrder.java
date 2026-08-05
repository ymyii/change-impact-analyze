package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.ModuleDependencyTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Converts Maven GraphML traversal into per-tier classpath order. */
final class ModuleClasspathOrder {

    private ModuleClasspathOrder() {
    }

    static List<String> reactorKeys(
            final ModuleDependencyTree tree,
            final Set<ArtifactCoord> reactorCoordinates) {
        final Set<String> reactor = reactorCoordinates.stream()
                .map(ArtifactCoord::diffKey)
                .collect(Collectors.toSet());
        final Set<String> result = new LinkedHashSet<>();
        collectReactor(tree.getDependencies(), reactor, result);
        return List.copyOf(result);
    }

    static List<ArtifactCoord> externalArtifacts(
            final DependencyAnalysisResult analysis,
            final ModuleDependencyTree tree,
            final Set<ArtifactCoord> reactorCoordinates) {
        final Set<String> reactor = reactorCoordinates.stream()
                .map(ArtifactCoord::diffKey)
                .collect(Collectors.toSet());
        final Map<ArtifactCoord, ArtifactCoord> bindings =
                new LinkedHashMap<>();
        analysis.artifactsFor(tree.getModulePath()).stream()
                .filter(value -> !reactor.contains(
                        value.getArtifact().diffKey()))
                .forEach(value -> bindings.put(
                        value.getArtifact(), value.getArtifact()));
        final List<ArtifactCoord> result = new ArrayList<>();
        collectExternal(tree.getDependencies(), reactor, bindings, result);
        if (result.size() != bindings.size()) {
            throw new IllegalStateException(
                    "Dependency traversal order is incomplete: module="
                            + tree.getModule() + "; ordered="
                            + result.size() + "; bindings="
                            + bindings.size());
        }
        return List.copyOf(result);
    }

    private static void collectReactor(
            final List<DependencyNode> nodes,
            final Set<String> reactor,
            final Set<String> result) {
        for (DependencyNode node : nodes) {
            if (reactor.contains(node.getArtifact().diffKey())) {
                result.add(node.getArtifact().diffKey());
            }
            collectReactor(node.getChildren(), reactor, result);
        }
    }

    private static void collectExternal(
            final List<DependencyNode> nodes,
            final Set<String> reactor,
            final Map<ArtifactCoord, ArtifactCoord> bindings,
            final List<ArtifactCoord> result) {
        for (DependencyNode node : nodes) {
            if (!reactor.contains(node.getArtifact().diffKey())) {
                final ArtifactCoord artifact = bindings.get(
                        node.getArtifact());
                if (artifact != null && !result.contains(artifact)) {
                    result.add(artifact);
                }
            }
            collectExternal(node.getChildren(), reactor, bindings, result);
        }
    }
}
