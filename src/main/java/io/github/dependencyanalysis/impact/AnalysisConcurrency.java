package io.github.dependencyanalysis.impact;

/**
 * Worker configuration recorded for one analysis run.
 *
 * @param configuredAnalysisParallelism configured safe-stage limit
 * @param actualModuleParallelism actual Module workers
 * @param actualJarDiffWorkers actual JAR diff workers
 * @param actualDecompileWorkers actual code evidence workers
 */
public record AnalysisConcurrency(
        int configuredAnalysisParallelism,
        int actualModuleParallelism,
        int actualJarDiffWorkers,
        int actualDecompileWorkers) {
}
