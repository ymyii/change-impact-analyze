package io.github.changeimpact.analyze.workspace;

/**
 * Immutable result of workspace preparation,
 * holding baseline and target side info.
 */
public final class WorkspaceResult {

    /** Baseline side info. */
    private final WorkspaceSideInfo baseline;

    /** Target side info. */
    private final WorkspaceSideInfo target;

    private WorkspaceResult(final Builder b) {
        this.baseline = b.baseline;
        this.target = b.target;
    }

    /**
     * Returns the baseline side info.
     *
     * @return baseline info
     */
    public WorkspaceSideInfo getBaseline() {
        return baseline;
    }

    /**
     * Returns the target side info.
     *
     * @return target info
     */
    public WorkspaceSideInfo getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "WorkspaceResult{"
                + "baseline=" + baseline
                + ", target=" + target
                + '}';
    }

    /**
     * Builder for {@link WorkspaceResult}.
     */
    public static final class Builder {

        /** Baseline side info. */
        private WorkspaceSideInfo baseline;

        /** Target side info. */
        private WorkspaceSideInfo target;

        /**
         * Sets the baseline side info.
         *
         * @param value baseline info
         * @return this builder
         */
        public Builder baseline(
                final WorkspaceSideInfo value) {
            this.baseline = value;
            return this;
        }

        /**
         * Sets the target side info.
         *
         * @param value target info
         * @return this builder
         */
        public Builder target(
                final WorkspaceSideInfo value) {
            this.target = value;
            return this;
        }

        /**
         * Builds the result.
         *
         * @return new instance
         */
        public WorkspaceResult build() {
            return new WorkspaceResult(this);
        }
    }
}
