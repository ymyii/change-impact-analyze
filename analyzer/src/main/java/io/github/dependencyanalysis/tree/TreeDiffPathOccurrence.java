package io.github.dependencyanalysis.tree;

record TreeDiffPathOccurrence(
        TreeDiffPathKey pathKey,
        String display,
        String resolvedVersion,
        String managedFromVersion,
        String scope,
        boolean direct) {
}
