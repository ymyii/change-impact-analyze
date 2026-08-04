package io.github.dependencyanalysis.tree;

import java.nio.file.Path;
import java.util.Objects;

// Wiki: wiki/features/git-workspace-management.md - snapshot path scope
/** Immutable Git repository snapshot with owned cleanup. */
public final class RepositorySnapshot
        implements AutoCloseable {

    /** Snapshot root used for analysis. */
    private final Path root;

    /** User repository root. */
    private final Path repositoryRoot;

    /** Analysis directory relative to the Git root. */
    private final Path analysisPath;

    /** Requested ref or current checkout. */
    private final String ref;

    /** Resolved commit. */
    private final String commit;

    /** Branch name. */
    private final String branch;

    /** Current checkout dirty flag. */
    private final boolean dirty;

    /** Cleanup callback. */
    private final Runnable cleanup;

    /**
     * Creates a snapshot.
     *
     * @param snapshotRoot analysis root
     * @param originalRoot original repository root
     * @param requestedRef requested ref
     * @param resolvedCommit commit
     * @param branchName branch
     * @param isDirty dirty flag
     * @param cleanupAction cleanup action
     */
    RepositorySnapshot(
            final Path snapshotRoot,
            final Path originalRoot,
            final String requestedRef,
            final String resolvedCommit,
            final String branchName,
            final boolean isDirty,
            final Runnable cleanupAction) {
        root = Objects.requireNonNull(
                snapshotRoot, "snapshotRoot");
        repositoryRoot = Objects.requireNonNull(
                originalRoot, "originalRoot");
        analysisPath = Path.of("");
        ref = requestedRef;
        commit = resolvedCommit;
        branch = branchName;
        dirty = isDirty;
        cleanup = cleanupAction;
    }

    private RepositorySnapshot(
            final RepositorySnapshot source,
            final Path relativeAnalysisPath) {
        root = source.root;
        repositoryRoot = source.repositoryRoot;
        analysisPath = Objects.requireNonNull(
                relativeAnalysisPath,
                "relativeAnalysisPath").normalize();
        if (analysisPath.isAbsolute()
                || analysisPath.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Analysis path must stay inside Git root");
        }
        ref = source.ref;
        commit = source.commit;
        branch = source.branch;
        dirty = source.dirty;
        cleanup = source.cleanup;
    }

    /**
     * Returns this snapshot scoped to one Git-root-relative directory.
     *
     * @param relativeAnalysisPath analysis directory
     * @return scoped snapshot sharing the same cleanup ownership
     */
    RepositorySnapshot withAnalysisPath(
            final Path relativeAnalysisPath) {
        return new RepositorySnapshot(
                this, relativeAnalysisPath);
    }

    /** @return snapshot root */
    public Path getRoot() {
        return root;
    }

    /** @return original repository root */
    public Path getRepositoryRoot() {
        return repositoryRoot;
    }

    /** @return analysis directory relative to Git root */
    public Path getAnalysisPath() {
        return analysisPath;
    }

    /** @return analysis directory inside this snapshot */
    public Path getAnalysisRoot() {
        return root.resolve(analysisPath).normalize();
    }

    /** @return requested ref, or current checkout */
    public String getRef() {
        return ref;
    }

    /** @return resolved commit */
    public String getCommit() {
        return commit;
    }

    /** @return branch name */
    public String getBranch() {
        return branch;
    }

    /** @return current checkout dirty flag */
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void close() {
        cleanup.run();
    }
}
