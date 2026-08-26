package io.github.dependencyanalysis.tree;

import java.util.List;

record TreeDiffPathKey(List<DependencyKey> nodes)
        implements Comparable<TreeDiffPathKey> {

    TreeDiffPathKey {
        nodes = List.copyOf(nodes);
    }

    @Override
    public int compareTo(final TreeDiffPathKey other) {
        final int maximum = Math.min(nodes.size(), other.nodes.size());
        for (int index = 0; index < maximum; index++) {
            final int compared = nodes.get(index)
                    .compareTo(other.nodes.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(nodes.size(), other.nodes.size());
    }
}
