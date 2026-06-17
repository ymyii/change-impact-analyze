package io.github.changeimpact.analyze.workspace;

import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Wiki: wiki/features/git-workspace-management.md - Git workspace 管理核心
/**
 * Manages git workspaces for baseline,
 * target and current analysis inputs.
 *
 * <p>Creates temporary git worktrees for
 * isolated analysis and cleans them up
 * on {@link #close()}.</p>
 */
public final class WorkspaceManager
        implements AutoCloseable {

    /** Stage name for diagnostics. */
    private static final String STAGE =
            "workspace";

    /** Temp directory prefix. */
    private static final String TEMP_PREFIX =
            "cia-ws-";

    /** Worktree directory name. */
    private static final String WT_NAME =
            "worktree";

    /** Max stderr summary length. */
    private static final int MAX_SUMMARY =
            200;

    /** Project root directory. */
    private final Path projectDir;

    /** Diagnostic collector. */
    private final DiagnosticCollector diag;

    /** Git runner for project directory. */
    private final GitCommandRunner runner;

    /** Git repository root directory. */
    private final Path gitRoot;

    /** Relative path from git root to project. */
    private final Path relativePath;

    /** Temporary parent dirs to clean. */
    private final List<Path> tempParents =
            new ArrayList<>();

    /** Whether close has been called. */
    private boolean closed;

    /**
     * Creates a manager for the given project.
     *
     * @param project     project root directory
     * @param diagnostics diagnostic collector
     * @throws IllegalStateException if project
     *     is not inside a git repository
     */
    public WorkspaceManager(
            final Path project,
            final DiagnosticCollector
                    diagnostics) {
        this.projectDir = Objects.requireNonNull(
                project, "project");
        this.diag = Objects.requireNonNull(
                diagnostics, "diagnostics");
        this.runner = new GitCommandRunner(
                project);
        this.gitRoot = resolveGitRoot();
        try {
            this.relativePath = gitRoot.relativize(
                    projectDir.toRealPath());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot resolve project"
                            + " path: " + projectDir,
                    e);
        }
    }

    /**
     * Prepares workspaces for baseline and
     * target sides.
     *
     * <p>If targetRef is null or blank, the
     * target side points to the current
     * workspace (not temporary).</p>
     *
     * @param baselineRef baseline branch or ref
     * @param targetRef   target branch/ref or null
     * @return workspace result
     * @throws WorkspacePrepareException if
     *     preparation fails
     */
    public WorkspaceResult prepare(
            final String baselineRef,
            final String targetRef)
            throws WorkspacePrepareException {
        diag.startStage(STAGE);
        try {
            final WorkspaceResult result =
                    doPrepare(
                            baselineRef,
                            targetRef);
            diag.endStage(STAGE);
            return result;
        } catch (WorkspacePrepareException e) {
            diag.failStage(STAGE,
                    e.getMessage());
            cleanupAll();
            throw e;
        }
    }

    /**
     * Cleans up all temporary worktrees.
     * Does not throw exceptions.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cleanupAll();
    }

    /**
     * Resolves the git repository root for
     * the project directory.
     *
     * @return git repository root path
     * @throws IllegalStateException if not
     *     inside a git repository
     */
    private Path resolveGitRoot() {
        try {
            final GitCommandResult res =
                    runner.run("rev-parse",
                            "--show-toplevel");
            if (res.getExitCode() != 0) {
                throw new IllegalStateException(
                        "Project directory is"
                                + " not inside"
                                + " a git"
                                + " repository: "
                                + projectDir);
            }
            return Path.of(
                    res.getStdout().trim());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to resolve git"
                            + " root for: "
                            + projectDir, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted resolving"
                            + " git root: "
                            + projectDir, e);
        }
    }

    private WorkspaceResult doPrepare(
            final String baselineRef,
            final String targetRef)
            throws WorkspacePrepareException {
        final String baseCommit =
                resolveCommit(
                        baselineRef,
                        WorkspaceSide.BASELINE,
                        null);
        diag.info(STAGE,
                "Baseline commit resolved: "
                        + baseCommit);

        final Path baseParent =
                createTempParent();
        final Path baseWt =
                baseParent.resolve(WT_NAME);
        createWorktree(
                baseWt,
                baseCommit,
                WorkspaceSide.BASELINE);
        diag.info(STAGE,
                "Baseline worktree created: "
                        + baseWt);

        final WorkspaceSideInfo baseInfo =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .BASELINE)
                        .path(baseWt.resolve(
                                relativePath))
                        .commit(baseCommit)
                        .whetherTemporary(true)
                        .build();

        final boolean hasTarget =
                targetRef != null
                        && !targetRef.isBlank();

        final WorkspaceSideInfo targetInfo;
        if (hasTarget) {
            targetInfo = prepareTargetWorktree(
                    targetRef);
        } else {
            targetInfo = prepareCurrentAsTarget();
        }

        return new WorkspaceResult.Builder()
                .baseline(baseInfo)
                .target(targetInfo)
                .build();
    }

    private WorkspaceSideInfo
            prepareTargetWorktree(
                    final String targetRef)
            throws WorkspacePrepareException {
        final String targetCommit =
                resolveCommit(
                        targetRef,
                        WorkspaceSide.TARGET,
                        null);
        diag.info(STAGE,
                "Target commit resolved: "
                        + targetCommit);

        final Path targetParent =
                createTempParent();
        final Path targetWt =
                targetParent.resolve(WT_NAME);
        try {
            createWorktree(
                    targetWt,
                    targetCommit,
                    WorkspaceSide.TARGET);
        } catch (WorkspacePrepareException e) {
            cleanupAll();
            throw e;
        }
        diag.info(STAGE,
                "Target worktree created: "
                        + targetWt);

        return new WorkspaceSideInfo.Builder()
                .side(WorkspaceSide.TARGET)
                .path(targetWt.resolve(
                        relativePath))
                .commit(targetCommit)
                .whetherTemporary(true)
                .build();
    }

    private WorkspaceSideInfo
            prepareCurrentAsTarget()
            throws WorkspacePrepareException {
        final String headCommit =
                resolveCommitInProject("HEAD");
        diag.info(STAGE,
                "Current HEAD resolved: "
                        + headCommit);
        return new WorkspaceSideInfo.Builder()
                .side(WorkspaceSide.TARGET)
                .path(projectDir)
                .commit(headCommit)
                .whetherTemporary(false)
                .build();
    }

    private String resolveCommit(
            final String ref,
            final WorkspaceSide s,
            final Path wtPath)
            throws WorkspacePrepareException {
        try {
            final GitCommandResult res =
                    runner.run("rev-parse",
                            "--verify", ref);
            if (res.getExitCode() != 0) {
                throw new WorkspacePrepareException(
                        s, ref, wtPath,
                        res.getExitCode(),
                        summarize(
                                res.getStderr()));
            }
            return res.getStdout().trim();
        } catch (IOException
                | InterruptedException e) {
            throw new WorkspacePrepareException(
                    s, ref, wtPath, -1,
                    e.getMessage());
        }
    }

    private String resolveCommitInProject(
            final String ref)
            throws WorkspacePrepareException {
        return resolveCommit(
                ref,
                WorkspaceSide.CURRENT,
                projectDir);
    }

    private void createWorktree(
            final Path wtPath,
            final String commit,
            final WorkspaceSide s)
            throws WorkspacePrepareException {
        try {
            final GitCommandResult res =
                    runner.run("worktree", "add",
                            "--detach",
                            wtPath.toString(),
                            commit);
            if (res.getExitCode() != 0) {
                throw new WorkspacePrepareException(
                        s, commit, wtPath,
                        res.getExitCode(),
                        summarize(
                                res.getStderr()));
            }
        } catch (IOException
                | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspacePrepareException(
                    s, commit, wtPath, -1,
                    e.getMessage());
        }
    }

    private Path createTempParent() {
        try {
            final Path dir =
                    Files.createTempDirectory(
                            TEMP_PREFIX);
            tempParents.add(dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create temp dir", e);
        }
    }

    private void cleanupAll() {
        final List<Path> snapshot =
                new ArrayList<>(tempParents);
        for (final Path parent : snapshot) {
            cleanupOne(parent);
        }
        tempParents.removeAll(snapshot);
    }

    private void cleanupOne(
            final Path parent) {
        final Path wt =
                parent.resolve(WT_NAME);
        if (Files.exists(wt)) {
            try {
                runner.run("worktree", "remove",
                        "--force",
                        wt.toString());
            } catch (IOException
                    | InterruptedException e) {
                diag.warn(STAGE,
                        "Failed to remove"
                                + " worktree: "
                                + wt
                                + ": "
                                + e.getMessage());
                Thread.currentThread()
                        .interrupt();
            }
        }
        forceDelete(parent);
    }

    private void forceDelete(
            final Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir,
                    new DeleteVisitor());
        } catch (IOException e) {
            diag.warn(STAGE,
                    "Failed to delete: "
                            + dir
                            + ": "
                            + e.getMessage());
        }
    }

    private static String summarize(
            final String text) {
        if (text == null) {
            return "";
        }
        final String trimmed = text.trim();
        if (trimmed.length() <= MAX_SUMMARY) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_SUMMARY)
                + "...";
    }

    /**
     * FileVisitor that force-deletes all
     * files and directories.
     */
    private static final class DeleteVisitor
            extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(
                final Path file,
                final BasicFileAttributes
                        attrs)
                throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult
                postVisitDirectory(
                        final Path dir,
                        final IOException exc)
                throws IOException {
            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
        }
    }
}
