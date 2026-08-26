package io.github.dependencyanalysis.tree;

import java.util.List;

record TreeDiffReactorResult(
        String reactorKey,
        String baselineCoordinate,
        String targetCoordinate,
        TreeDiffSideState baselineState,
        TreeDiffSideState targetState,
        TreeDiffComparisonStatus comparisonStatus,
        List<TreeDiffModuleResult> modules,
        TreeDiffMetrics metrics,
        List<String> issues) {

    TreeDiffReactorResult {
        modules = List.copyOf(modules);
        issues = List.copyOf(issues);
    }

    /** @return number of comparable Modules */
    long comparableModules() {
        return modules.stream().filter(module -> module.comparisonStatus()
                == TreeDiffComparisonStatus.COMPARABLE).count();
    }
}
