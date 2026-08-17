package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry;
import io.github.dependencyanalysis.dependency.DependencyArtifactSelection;
import io.github.dependencyanalysis.runtime.ReportCache;

import java.nio.file.Path;

/**
 * Runtime controls for the per-module pipeline.
 *
 * @param callGraphTimeoutSeconds per-module timeout
 * @param analysisParallelism configured global analysis concurrency limit
 * @param outputPaths command temporary and optional diagnostics paths
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param kObjDepth command-wide k-object receiver allocation-string depth
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyAnalysisScope dependency method-body scope
 * @param jdkModel command-wide JDK Method Model selection
 * @param dependencySelection changed Maven dependency source boundary
 * @param executors Analyzer-owned pool registry
 * @param reportCache command report cache, nullable for compatibility callers
 */
record PerModulePipelineOptions(
        long callGraphTimeoutSeconds,
        int analysisParallelism,
        PipelineOutputPaths outputPaths,
        EntrypointSelection entrypointSelection,
        CallGraphAlgorithm callGraphAlgorithm,
        int kObjDepth,
        WalaReflectionOptions reflectionOptions,
        DependencyAnalysisScopeMode dependencyAnalysisScope,
        JdkModelSelection jdkModel,
        DependencyArtifactSelection dependencySelection,
        ManagedExecutorRegistry executors,
        ReportCache reportCache) {

    /** @return command temporary directory */
    Path temporaryDirectory() {
        return outputPaths.temporaryDirectory();
    }

    /** @return optional benchmark diagnostics JSON path */
    Path callGraphDiagnosticsOutput() {
        return outputPaths.callGraphDiagnosticsOutput();
    }
}
