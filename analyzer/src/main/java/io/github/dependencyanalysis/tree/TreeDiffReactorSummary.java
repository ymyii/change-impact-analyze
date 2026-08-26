package io.github.dependencyanalysis.tree;

import java.util.List;

record TreeDiffReactorSummary(
        String reactorKey,
        String file,
        TreeDiffComparisonStatus comparisonStatus,
        TreeDiffMetrics metrics,
        int moduleCount,
        int comparableModules,
        int structureMismatchModules,
        int unavailableModules,
        int shardCount,
        long shardBytes,
        List<String> issues,
        List<String> technicalWarnings) {

    TreeDiffReactorSummary {
        issues = List.copyOf(issues);
        technicalWarnings = List.copyOf(technicalWarnings);
    }
}
