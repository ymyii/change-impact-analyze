package io.github.dependencyanalysis.callgraph;

/**
 * Entrypoint construction metrics.
 *
 * @param entrypointCount PROJECT entrypoints
 * @param parameterCandidateCount concrete parameter candidates
 */
record EntrypointMetrics(
        int entrypointCount,
        int parameterCandidateCount) {
}
