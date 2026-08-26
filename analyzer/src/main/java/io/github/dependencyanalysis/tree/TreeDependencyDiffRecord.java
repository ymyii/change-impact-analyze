package io.github.dependencyanalysis.tree;

import java.util.List;

record TreeDependencyDiffRecord(
        DependencyKey key,
        TreeDiffSideDependency baseline,
        TreeDiffSideDependency target,
        TreeDependencyBaseChangeType baseChangeType,
        boolean scopeChanged,
        List<TreeDiffChainRow> chains) {

    TreeDependencyDiffRecord {
        chains = List.copyOf(chains);
    }
}
