package io.github.dependencyanalysis.workspace;

import java.nio.file.Path;

/**
 * Thrown when workspace preparation fails.
 * Carries diagnostic fields for reporting.
 */
public class WorkspacePrepareException
        extends Exception {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /** Side that failed. */
    private final WorkspaceSide side;

    /** Commit ref that was requested. */
    private final String commit;

    /** Worktree path (may be null). */
    private final Path worktreePath;

    /** Git process exit code. */
    private final int gitExitCode;

    /** Failure reason. */
    private final String reason;

    /**
     * Creates a new exception.
     *
     * @param s         side that failed
     * @param cmt       commit ref
     * @param path      worktree path or null
     * @param exitCode  git exit code
     * @param failureReason    failure reason
     */
    public WorkspacePrepareException(
            final WorkspaceSide s,
            final String cmt,
            final Path path,
            final int exitCode,
            final String failureReason) {
        super(buildMessage(
                s, cmt, path, exitCode, failureReason));
        this.side = s;
        this.commit = cmt;
        this.worktreePath = path;
        this.gitExitCode = exitCode;
        this.reason = failureReason;
    }

    /**
     * Returns the side that failed.
     *
     * @return side
     */
    public WorkspaceSide getSide() {
        return side;
    }

    /**
     * Returns the requested commit ref.
     *
     * @return commit ref
     */
    public String getCommit() {
        return commit;
    }

    /**
     * Returns the worktree path or null.
     *
     * @return path or null
     */
    public Path getWorktreePath() {
        return worktreePath;
    }

    /**
     * Returns the git exit code.
     *
     * @return exit code
     */
    public int getGitExitCode() {
        return gitExitCode;
    }

    /**
     * Returns the failure reason.
     *
     * @return reason text
     */
    public String getReason() {
        return reason;
    }

    private static String buildMessage(
            final WorkspaceSide s,
            final String cmt,
            final Path path,
            final int exitCode,
            final String failureReason) {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("Workspace prepare failed")
                .append(": side=")
                .append(s)
                .append(", commit=")
                .append(cmt)
                .append(", path=")
                .append(path)
                .append(", exitCode=")
                .append(exitCode)
                .append(", reason=")
                .append(failureReason);
        return sb.toString();
    }
}
