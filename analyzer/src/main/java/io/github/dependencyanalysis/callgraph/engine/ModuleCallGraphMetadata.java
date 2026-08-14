package io.github.dependencyanalysis.callgraph.engine;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundaryMetadata;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelectionMetrics;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.StrategyModelMetadata;
import io.github.dependencyanalysis.callgraph.topology.CallGraphTopologySnapshot;
import java.util.Objects;

/**
 * Immutable metrics and fixed-point model output from one graph build.
 *
 * @param algorithm effective Call Graph algorithm
 * @param stats graph build metrics
 * @param entrypoints entrypoint selection metrics
 * @param strategyModels immutable algorithm model metadata
 * @param dependencyBoundary dependency body boundary metadata
 * @param topology optional read-only benchmark topology capture
 */
record ModuleCallGraphMetadata(
        CallGraphAlgorithm algorithm,
        CallGraphStats stats,
        EntrypointSelectionMetrics entrypoints,
        StrategyModelMetadata strategyModels,
        DependencyBodyBoundaryMetadata dependencyBoundary,
        CallGraphTopologySnapshot topology) {

    ModuleCallGraphMetadata {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(entrypoints, "entrypoints");
        Objects.requireNonNull(strategyModels, "strategyModels");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
    }
}
