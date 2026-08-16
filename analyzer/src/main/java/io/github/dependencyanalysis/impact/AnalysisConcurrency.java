package io.github.dependencyanalysis.impact;

/**
 * Worker configuration recorded for one analysis run.
 *
 * @param configuredAnalysisParallelism configured global operation limit
 * @param actualJarDiffWorkers actual JAR diff workers
 * @param actualImpactQueryWorkers actual Impact Query workers
 * @param actualDecompileWorkers actual code evidence workers
 */
public record AnalysisConcurrency(
        int configuredAnalysisParallelism,
        int actualJarDiffWorkers,
        int actualImpactQueryWorkers,
        int actualDecompileWorkers) {
}
