package io.github.dependencyanalysis.tree;

/** Composite availability of a Reactor or Module pair. */
enum TreeDiffComparisonStatus {
    /** Both sides exist and are comparable. */
    COMPARABLE,
    /** Exactly one side exists. */
    STRUCTURE_MISMATCH,
    /** At least one side cannot be determined. */
    UNAVAILABLE;

    /**
     * Resolves the canonical composite status.
     *
     * @param baseline baseline state
     * @param target target state
     * @return composite status
     */
    static TreeDiffComparisonStatus of(
            final TreeDiffSideState baseline,
            final TreeDiffSideState target) {
        if (baseline == TreeDiffSideState.UNAVAILABLE
                || target == TreeDiffSideState.UNAVAILABLE) {
            return UNAVAILABLE;
        }
        if (baseline == TreeDiffSideState.PRESENT
                && target == TreeDiffSideState.PRESENT) {
            return COMPARABLE;
        }
        return STRUCTURE_MISMATCH;
    }
}
