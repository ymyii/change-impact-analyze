package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts selected dependency evidence into per-tier classpath order. */
final class ModuleClasspathOrder {

    private ModuleClasspathOrder() {
    }

    static List<String> reactorKeys(
            final ModuleDependencyEvidence evidence) {
        return evidence.getSelectedReactorKeys();
    }

    static List<ArtifactCoord> externalArtifacts(
            final ModuleDependencyEvidence evidence) {
        final Map<ArtifactCoord, ArtifactCoord> bindings =
                new LinkedHashMap<>();
        evidence.getArtifacts().forEach(value -> bindings.put(
                value.getArtifact(), value.getArtifact()));
        final List<ArtifactCoord> result = new ArrayList<>();
        collectExternal(evidence.getDependencies(), bindings, result);
        if (result.size() != bindings.size()) {
            throw new IllegalStateException(
                    "Selected dependency traversal is incomplete: module="
                            + evidence.getModule() + "; ordered="
                            + result.size() + "; bindings="
                            + bindings.size());
        }
        return List.copyOf(result);
    }

    private static void collectExternal(
            final List<DependencyNode> nodes,
            final Map<ArtifactCoord, ArtifactCoord> bindings,
            final List<ArtifactCoord> result) {
        for (DependencyNode node : nodes) {
            final ArtifactCoord artifact = bindings.get(node.getArtifact());
            if (artifact != null && !result.contains(artifact)) {
                result.add(artifact);
            }
            collectExternal(node.getChildren(), bindings, result);
        }
    }
}
