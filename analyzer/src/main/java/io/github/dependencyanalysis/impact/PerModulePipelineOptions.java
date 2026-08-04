package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.EntrypointSelection;

import java.nio.file.Path;

/**
 * Runtime controls for the per-module pipeline.
 *
 * @param callGraphTimeoutSeconds per-module timeout
 * @param analysisParallelism configured safe parallel stage limit
 * @param temporaryDirectory command temporary directory
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 */
record PerModulePipelineOptions(
        long callGraphTimeoutSeconds,
        int analysisParallelism,
        Path temporaryDirectory,
        EntrypointSelection entrypointSelection) {
}
