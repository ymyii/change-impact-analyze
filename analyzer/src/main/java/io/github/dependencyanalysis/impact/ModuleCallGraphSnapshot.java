package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.engine.CallGraphStats;

import java.util.Objects;

/**
 * Lightweight metrics retained after a live WALA session is released.
 *
 * @param stats graph build statistics
 * @param entrypointCount entrypoint count
 * @param parameterCandidateCount parameter candidate count
 * @param selectedEntrypointClassCount selected PROJECT class count
 * @param contextCount distinct rendered Context count
 * @param jdkDeclaredDispatchPrunedTargetCount fixed CHA pruning count
 * @param dependencyBoundary dependency boundary metrics
 */
public record ModuleCallGraphSnapshot(
        CallGraphStats stats,
        int entrypointCount,
        int parameterCandidateCount,
        int selectedEntrypointClassCount,
        long contextCount,
        int jdkDeclaredDispatchPrunedTargetCount,
        DependencyBoundarySnapshot dependencyBoundary) {

    /** Validates non-negative metrics. */
    public ModuleCallGraphSnapshot {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
        if (entrypointCount < 0 || parameterCandidateCount < 0
                || selectedEntrypointClassCount < 0 || contextCount < 0
                || jdkDeclaredDispatchPrunedTargetCount < 0) {
            throw new IllegalArgumentException(
                    "negative Module Call Graph snapshot metric");
        }
    }
}
