package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry;

import java.nio.file.Path;

/**
 * Runtime controls for the per-module pipeline.
 *
 * @param callGraphTimeoutSeconds per-module timeout
 * @param analysisParallelism configured safe parallel stage limit
 * @param temporaryDirectory command temporary directory
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param executors Analyzer-owned pool registry
 */
record PerModulePipelineOptions(
        long callGraphTimeoutSeconds,
        int analysisParallelism,
        Path temporaryDirectory,
        EntrypointSelection entrypointSelection,
        CallGraphAlgorithm callGraphAlgorithm,
        WalaReflectionOptions reflectionOptions,
        ManagedExecutorRegistry executors) {

    PerModulePipelineOptions(
            final long timeoutSeconds,
            final int parallelism,
            final Path tempDirectory,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final ManagedExecutorRegistry executorRegistry) {
        this(timeoutSeconds, parallelism,
                tempDirectory, selection, algorithm,
                WalaReflectionOptions.defaultOptions(), executorRegistry);
    }
}
