package io.github.dependencyanalysis.callgraph.engine;

import io.github.dependencyanalysis.callgraph.scope.CallGraphChange;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyScope;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Minimal immutable module projection accepted by the Call Graph engine.
 *
 * @param moduleLabel stable module label
 * @param projectClasses project class directory
 * @param reactorDependencyClasses reactor dependency class directories
 * @param targetArtifacts selected target artifacts
 * @param dependencyScope external method-body scope
 * @param changes graph-relevant changed members
 */
public record ModuleCallGraphInput(
        String moduleLabel,
        Path projectClasses,
        List<Path> reactorDependencyClasses,
        List<ArtifactCoord> targetArtifacts,
        CallGraphDependencyScope dependencyScope,
        List<CallGraphChange> changes) {

    /** Validates and freezes the graph input. */
    public ModuleCallGraphInput {
        Objects.requireNonNull(moduleLabel, "moduleLabel");
        Objects.requireNonNull(projectClasses, "projectClasses");
        reactorDependencyClasses = List.copyOf(Objects.requireNonNull(
                reactorDependencyClasses, "reactorDependencyClasses"));
        targetArtifacts = List.copyOf(Objects.requireNonNull(
                targetArtifacts, "targetArtifacts"));
        Objects.requireNonNull(dependencyScope, "dependencyScope");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
