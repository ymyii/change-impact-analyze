package io.github.dependencyanalysis.impact;

/**
 * Worker configuration recorded for one analysis run.
 *
 * @param configuredModuleParallelism configured Module limit
 * @param actualModuleParallelism actual Module workers
 * @param configuredJarDiffWorkers configured JAR diff workers
 * @param actualJarDiffWorkers actual JAR diff workers
 */
public record AnalysisConcurrency(
        int configuredModuleParallelism,
        int actualModuleParallelism,
        int configuredJarDiffWorkers,
        int actualJarDiffWorkers) {
}
