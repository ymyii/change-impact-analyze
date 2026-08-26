package io.github.dependencyanalysis.workspace;

import java.nio.file.Path;

/**
 * Immutable descriptor for one side of a
 * workspace comparison.
 */
public final class WorkspaceSideInfo {

    /** Which side this info describes. */
    private final WorkspaceSide side;

    /** Filesystem path of the workspace. */
    private final Path path;

    /** Resolved commit hash. */
    private final String commit;

    /** Dirty state captured before analysis. */
    private final boolean dirty;

    /** True if a temporary worktree was created. */
    private final boolean whetherTemporary;

    private WorkspaceSideInfo(
            final Builder b) {
        this.side = b.side;
        this.path = b.path;
        this.commit = b.commit;
        this.dirty = b.dirty;
        this.whetherTemporary = b.whetherTemporary;
    }

    /**
     * Returns the side.
     *
     * @return side enum value
     */
    public WorkspaceSide getSide() {
        return side;
    }

    /**
     * Returns the workspace path.
     *
     * @return path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the resolved commit hash.
     *
     * @return commit hash
     */
    public String getCommit() {
        return commit;
    }

    /** @return dirty state captured before analysis */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Returns whether this workspace is a
     * temporary worktree.
     *
     * @return true if temporary
     */
    public boolean isWhetherTemporary() {
        return whetherTemporary;
    }

    @Override
    public String toString() {
        return "WorkspaceSideInfo{"
                + "side=" + side
                + ", path=" + path
                + ", commit='" + commit + '\''
                + ", dirty=" + dirty
                + ", whetherTemporary="
                + whetherTemporary
                + '}';
    }

    /**
     * Builder for {@link WorkspaceSideInfo}.
     */
    public static final class Builder {

        /** Side. */
        private WorkspaceSide side;

        /** Path. */
        private Path path;

        /** Commit hash. */
        private String commit;

        /** Dirty state. */
        private boolean dirty;

        /** Whether temporary. */
        private boolean whetherTemporary;

        /**
         * Sets the side.
         *
         * @param value side
         * @return this builder
         */
        public Builder side(
                final WorkspaceSide value) {
            this.side = value;
            return this;
        }

        /**
         * Sets the path.
         *
         * @param value path
         * @return this builder
         */
        public Builder path(final Path value) {
            this.path = value;
            return this;
        }

        /**
         * Sets the commit hash.
         *
         * @param value commit
         * @return this builder
         */
        public Builder commit(
                final String value) {
            this.commit = value;
            return this;
        }

        /**
         * Sets the dirty state captured before analysis.
         *
         * @param value dirty state
         * @return this builder
         */
        public Builder dirty(final boolean value) {
            dirty = value;
            return this;
        }

        /**
         * Sets whether temporary.
         *
         * @param value true if temporary
         * @return this builder
         */
        public Builder whetherTemporary(
                final boolean value) {
            this.whetherTemporary = value;
            return this;
        }

        /**
         * Builds the side info.
         *
         * @return new instance
         */
        public WorkspaceSideInfo build() {
            return new WorkspaceSideInfo(this);
        }
    }
}
