package io.github.dependencyanalysis.callgraph;

/**
 * @param selectedClassCount selected PROJECT classes
 * @param entrypointCount selected non-abstract declared methods
 * @param parameterCandidateCount complete-scope parameter candidates
 */
public record EntrypointSelectionMetrics(
        int selectedClassCount,
        int entrypointCount,
        int parameterCandidateCount) {
}
