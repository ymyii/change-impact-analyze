package io.github.dependencyanalysis.callgraph;

import java.util.List;
import java.util.Objects;

import io.github.dependencyanalysis.impact.StructuralScanResult;

/**
 * Immutable metrics and fixed-point model output from one graph build.
 *
 * @param stats graph build metrics
 * @param entrypoints entrypoint selection metrics
 * @param dynamicEvidence reachable invokedynamic evidence
 * @param dynamicLimitations invokedynamic limitations
 * @param serviceLoader ServiceLoader model metadata
 * @param structuralScan pre-graph structural metadata evidence
 */
record ModuleCallGraphMetadata(
        CallGraphStats stats,
        EntrypointSelectionMetrics entrypoints,
        DynamicCallEvidenceIndex dynamicEvidence,
        List<String> dynamicLimitations,
        ServiceLoaderFixedPointModel serviceLoader,
        StructuralScanResult structuralScan) {

    ModuleCallGraphMetadata {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(entrypoints, "entrypoints");
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        dynamicLimitations = List.copyOf(Objects.requireNonNull(
                dynamicLimitations, "dynamicLimitations"));
        Objects.requireNonNull(serviceLoader, "serviceLoader");
        Objects.requireNonNull(structuralScan, "structuralScan");
    }
}
