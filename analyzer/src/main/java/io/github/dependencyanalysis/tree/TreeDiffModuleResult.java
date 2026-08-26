package io.github.dependencyanalysis.tree;

import java.util.List;

record TreeDiffModuleResult(
        String moduleKey,
        String baselineCoordinate,
        String targetCoordinate,
        TreeDiffSideState baselineState,
        TreeDiffSideState targetState,
        TreeDiffComparisonStatus comparisonStatus,
        TreeDiffMetrics metrics,
        List<TreeDependencyDiffRecord> dependencies,
        ModuleTreeResult baselineModule,
        ModuleTreeResult targetModule,
        List<String> issues) {

    TreeDiffModuleResult {
        dependencies = List.copyOf(dependencies);
        issues = List.copyOf(issues);
    }
}
