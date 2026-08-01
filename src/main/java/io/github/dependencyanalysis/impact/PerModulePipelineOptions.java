package io.github.dependencyanalysis.impact;

import java.nio.file.Path;

/**
 * Runtime controls for the per-module pipeline.
 *
 * @param callGraphTimeoutSeconds per-module timeout
 * @param moduleParallelism configured module parallelism
 * @param temporaryDirectory command temporary directory
 */
record PerModulePipelineOptions(
        long callGraphTimeoutSeconds,
        int moduleParallelism,
        Path temporaryDirectory) {
}
