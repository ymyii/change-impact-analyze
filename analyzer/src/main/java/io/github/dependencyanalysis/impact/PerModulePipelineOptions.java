package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;

import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphPolicy;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry;
import io.github.dependencyanalysis.runtime.ReportCache;

import java.nio.file.Path;

/**
 * Runtime controls for the per-module pipeline.
 *
 * @param callGraphTimeoutSeconds per-module timeout
 * @param analysisParallelism configured safe parallel stage limit
 * @param outputPaths command temporary and optional diagnostics paths
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param kObjDepth command-wide k-object receiver allocation-string depth
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyAnalysisScope dependency method-body scope
 * @param jdkModel command-wide JDK Method Model selection
 * @param resultRefinements command-wide result-refinement selection
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
        ResultRefinementSelection resultRefinements,
        ManagedExecutorRegistry executors,
        ReportCache reportCache) {

    /** Compatibility constructor using the default k-object depth. */
    PerModulePipelineOptions(
            final long timeoutSeconds,
            final int parallelism,
            final PipelineOutputPaths paths,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection selectedJdkModel,
            final ManagedExecutorRegistry executorRegistry) {
        this(timeoutSeconds, parallelism, paths, selection, algorithm,
                CallGraphAlgorithm.defaultKObjDepth(), reflection,
                dependencyScope, selectedJdkModel,
                ResultRefinementSelection.defaultSelection(),
                executorRegistry, null);
    }

    /** Compatibility constructor using the default JDK model. */
    PerModulePipelineOptions(
            final long timeoutSeconds,
            final int parallelism,
            final PipelineOutputPaths paths,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope,
            final ManagedExecutorRegistry executorRegistry) {
        this(timeoutSeconds, parallelism, paths, selection, algorithm,
                CallGraphAlgorithm.defaultKObjDepth(), reflection,
                dependencyScope,
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection(),
                executorRegistry, null);
    }

    PerModulePipelineOptions(
            final long timeoutSeconds,
            final int parallelism,
            final Path tempDirectory,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final ManagedExecutorRegistry executorRegistry) {
        this(timeoutSeconds, parallelism,
                new PipelineOutputPaths(tempDirectory, null),
                selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                reflection,
                DependencyAnalysisScopeMode.defaultMode(),
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection(),
                executorRegistry, null);
    }

    PerModulePipelineOptions(
            final long timeoutSeconds,
            final int parallelism,
            final Path tempDirectory,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final ManagedExecutorRegistry executorRegistry) {
        this(timeoutSeconds, parallelism,
                new PipelineOutputPaths(tempDirectory, null),
                selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                WalaReflectionOptions.defaultOptions(),
                DependencyAnalysisScopeMode.defaultMode(),
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection(),
                executorRegistry, null);
    }

    /** @return command temporary directory */
    Path temporaryDirectory() {
        return outputPaths.temporaryDirectory();
    }

    /** @return optional benchmark diagnostics JSON path */
    Path callGraphDiagnosticsOutput() {
        return outputPaths.callGraphDiagnosticsOutput();
    }
}
