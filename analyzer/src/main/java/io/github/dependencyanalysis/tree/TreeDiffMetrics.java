package io.github.dependencyanalysis.tree;

record TreeDiffMetrics(
        int versionChanged,
        int added,
        int removed,
        int resolvedUnchanged,
        int scopeChanged) {

    /** Empty metrics. */
    static final TreeDiffMetrics ZERO =
            new TreeDiffMetrics(0, 0, 0, 0, 0);

    /** @return dependency rows covered by base classifications */
    int totalDependencies() {
        return versionChanged + added + removed + resolvedUnchanged;
    }

    /** @return component-wise sum */
    TreeDiffMetrics plus(final TreeDiffMetrics other) {
        return new TreeDiffMetrics(
                versionChanged + other.versionChanged,
                added + other.added,
                removed + other.removed,
                resolvedUnchanged + other.resolvedUnchanged,
                scopeChanged + other.scopeChanged);
    }

    /** Mutable builder used only during deterministic projection. */
    static final class Builder {
        /** Version changed. */
        private int versionChanged;
        /** Added. */
        private int added;
        /** Removed. */
        private int removed;
        /** Resolved unchanged. */
        private int resolvedUnchanged;
        /** Scope changed. */
        private int scopeChanged;

        /**
         * Adds one dependency classification.
         *
         * @param type base change type
         * @param changedScope whether scope changed
         */
        void add(
                final TreeDependencyBaseChangeType type,
                final boolean changedScope) {
            switch (type) {
                case VERSION_CHANGED -> versionChanged++;
                case ADDED -> added++;
                case REMOVED -> removed++;
                case RESOLVED_UNCHANGED -> resolvedUnchanged++;
                default -> throw new IllegalStateException(
                        "Unsupported base change type: " + type);
            }
            if (changedScope) {
                scopeChanged++;
            }
        }

        /** @return immutable metrics */
        TreeDiffMetrics build() {
            return new TreeDiffMetrics(versionChanged, added, removed,
                    resolvedUnchanged, scopeChanged);
        }
    }
}
