package io.github.dependencyanalysis.impact;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Command-owned pipeline output paths.
 *
 * @param temporaryDirectory command temporary directory
 * @param callGraphDiagnosticsOutput optional benchmark diagnostics JSON
 */
record PipelineOutputPaths(
        Path temporaryDirectory,
        Path callGraphDiagnosticsOutput) {

    PipelineOutputPaths {
        Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
    }
}
