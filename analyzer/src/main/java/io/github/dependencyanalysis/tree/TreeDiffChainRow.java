package io.github.dependencyanalysis.tree;

record TreeDiffChainRow(
        TreeDiffPathOccurrence baseline,
        TreeDiffPathOccurrence target,
        TreeChainChangeType changeType) {
}
