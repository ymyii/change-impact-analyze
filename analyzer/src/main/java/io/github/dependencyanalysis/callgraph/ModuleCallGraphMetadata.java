package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

import io.github.dependencyanalysis.impact.StructuralReferenceIndex;

/**
 * Immutable metrics and fixed-point model output from one graph build.
 *
 * @param stats graph build metrics
 * @param entrypoints entrypoint selection metrics
 * @param strategyModels immutable algorithm model metadata
 * @param structuralReferences pre-graph raw structural metadata index
 * @param topology optional read-only benchmark topology capture
 */
record ModuleCallGraphMetadata(
        CallGraphStats stats,
        EntrypointSelectionMetrics entrypoints,
        StrategyModelMetadata strategyModels,
        StructuralReferenceIndex structuralReferences,
        CallGraphTopologySnapshot topology) {

    ModuleCallGraphMetadata {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(entrypoints, "entrypoints");
        Objects.requireNonNull(strategyModels, "strategyModels");
        Objects.requireNonNull(structuralReferences, "structuralReferences");
    }
}
