package io.github.dependencyanalysis.tree;

record TreeDiffSideMetadata(
        String kind,
        String ref,
        String commit,
        boolean dirty) {

    /** @return user-facing side name */
    String displayName() {
        return "current-workspace".equals(kind) ? "Current workspace" : ref;
    }
}
