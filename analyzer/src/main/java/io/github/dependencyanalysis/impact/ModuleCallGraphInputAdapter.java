package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphInput;
import io.github.dependencyanalysis.callgraph.scope.CallGraphChange;
import io.github.dependencyanalysis.callgraph.scope.CallGraphChangeKind;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyPath;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyScope;
import io.github.dependencyanalysis.callgraph.scope.DependencyBodyPolicy;
import io.github.dependencyanalysis.callgraph.scope.DependencyScopeMode;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects business analysis state into the Call Graph input contract. */
public final class ModuleCallGraphInputAdapter {

    /**
     * @param unit business module analysis unit
     * @return immutable minimal graph input
     */
    public ModuleCallGraphInput adapt(final ModuleAnalysisUnit unit) {
        final Map<ArtifactCoord, DependencyBodyPolicy> policies =
                new LinkedHashMap<>();
        for (ArtifactCoord artifact : unit.getTargetArtifacts()) {
            policies.put(artifact, unit.getChangedPathSelection()
                    .policyFor(artifact) == DependencyMethodBodyPolicy.REAL_IR
                    ? DependencyBodyPolicy.REAL_IR
                    : DependencyBodyPolicy.NO_OP);
        }
        final List<CallGraphDependencyPath> paths = unit
                .getChangedPathSelection().paths().stream()
                .map(path -> new CallGraphDependencyPath(
                        path.seed(), path.stablePath()))
                .toList();
        final CallGraphDependencyScope scope = new CallGraphDependencyScope(
                unit.getChangedPathSelection().actualMode()
                        == DependencyAnalysisScopeMode.CHANGED_PATHS
                        ? DependencyScopeMode.CHANGED_PATHS
                        : DependencyScopeMode.FULL,
                policies, paths);
        final List<CallGraphChange> changes = unit.getChangePoints().stream()
                .map(bound -> {
                    final var point = bound.getChangePoint();
                    return new CallGraphChange(point.getArtifact(),
                            kind(point.getKind()), point.getOwner(),
                            point.getName(), point.getNewDescriptor());
                }).toList();
        return new ModuleCallGraphInput(unit.getModuleId().stableKey(),
                unit.getProjectClasses(), unit.getReactorDependencyClasses(),
                unit.getTargetArtifacts(), scope, changes);
    }

    private CallGraphChangeKind kind(final ChangePointKind kind) {
        return switch (kind) {
            case METHOD_ADDED -> CallGraphChangeKind.METHOD_ADDED;
            case METHOD_BODY_CHANGED ->
                    CallGraphChangeKind.METHOD_BODY_CHANGED;
            case METHOD_ACCESS_NARROWED ->
                    CallGraphChangeKind.METHOD_ACCESS_NARROWED;
            default -> CallGraphChangeKind.OTHER;
        };
    }
}
