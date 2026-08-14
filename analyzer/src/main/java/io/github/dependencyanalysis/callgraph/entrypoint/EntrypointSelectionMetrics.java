package io.github.dependencyanalysis.callgraph.entrypoint;

/**
 * @param selectedClassCount selected PROJECT classes
 * @param entrypointCount selected non-abstract declared methods
 * @param parameterCandidateCount declared-type parameter candidates
 */
public record EntrypointSelectionMetrics(
        int selectedClassCount,
        int entrypointCount,
        int parameterCandidateCount) {
}
