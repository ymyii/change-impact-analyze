package io.github.dependencyanalysis.tree;

import java.util.NavigableMap;
import java.util.TreeMap;

record TreeDiffSideDependency(
        DependencyKey key,
        String resolvedVersion,
        String scope,
        boolean direct,
        NavigableMap<TreeDiffPathKey, TreeDiffPathOccurrence> paths) {

    TreeDiffSideDependency {
        paths = java.util.Collections.unmodifiableNavigableMap(
                new TreeMap<>(paths));
    }
}
