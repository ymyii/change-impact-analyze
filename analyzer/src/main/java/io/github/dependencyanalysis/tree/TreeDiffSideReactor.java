package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.RepositoryInventory;

record TreeDiffSideReactor(
        TreeDiffSideState state,
        ReactorDescriptor descriptor,
        RepositoryInventory inventory,
        ReactorTreeResult collection,
        String issue) {

    /** @return side absent from an available inventory */
    static TreeDiffSideReactor absent(final RepositoryInventory inventory) {
        return new TreeDiffSideReactor(TreeDiffSideState.ABSENT,
                null, inventory, null, "");
    }

    /** @return side inventory unavailable */
    static TreeDiffSideReactor unavailable(final String issue) {
        return new TreeDiffSideReactor(TreeDiffSideState.UNAVAILABLE,
                null, null, null, issue);
    }
}
