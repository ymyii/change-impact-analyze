package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

import io.github.dependencyanalysis.impact.StructuralReferenceIndex;
import io.github.dependencyanalysis.impact.ChangePointEvidenceIndex;

/**
 * Immutable metrics and fixed-point model output from one graph build.
 *
 * @param algorithm effective Call Graph algorithm
 * @param stats graph build metrics
 * @param entrypoints entrypoint selection metrics
 * @param strategyModels immutable algorithm model metadata
 * @param dependencyBoundary dependency body boundary metadata
 * @param structuralReferences pre-graph raw structural metadata index
 * @param changePointEvidence post-graph frozen terminal evidence
 * @param topology optional read-only benchmark topology capture
 */
record ModuleCallGraphMetadata(
        CallGraphAlgorithm algorithm,
        CallGraphStats stats,
        EntrypointSelectionMetrics entrypoints,
        StrategyModelMetadata strategyModels,
        DependencyBodyBoundaryMetadata dependencyBoundary,
        StructuralReferenceIndex structuralReferences,
        ChangePointEvidenceIndex changePointEvidence,
        CallGraphTopologySnapshot topology) {

    ModuleCallGraphMetadata {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(entrypoints, "entrypoints");
        Objects.requireNonNull(strategyModels, "strategyModels");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
        Objects.requireNonNull(structuralReferences, "structuralReferences");
        Objects.requireNonNull(changePointEvidence, "changePointEvidence");
    }
}
