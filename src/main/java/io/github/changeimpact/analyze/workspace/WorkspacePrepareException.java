package io.github.changeimpact.analyze.workspace;

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

    /** Git stderr summary. */
    private final String gitStderr;

    /**
     * Creates a new exception.
     *
     * @param s         side that failed
     * @param cmt       commit ref
     * @param path      worktree path or null
     * @param exitCode  git exit code
     * @param stderr    git stderr output
     */
    public WorkspacePrepareException(
            final WorkspaceSide s,
            final String cmt,
            final Path path,
            final int exitCode,
            final String stderr) {
        super(buildMessage(
                s, cmt, path, exitCode, stderr));
        this.side = s;
        this.commit = cmt;
        this.worktreePath = path;
        this.gitExitCode = exitCode;
        this.gitStderr = stderr;
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
     * Returns the git stderr summary.
     *
     * @return stderr text
     */
    public String getGitStderr() {
        return gitStderr;
    }

    private static String buildMessage(
            final WorkspaceSide s,
            final String cmt,
            final Path path,
            final int exitCode,
            final String stderr) {
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
                .append(", stderr=")
                .append(stderr);
        return sb.toString();
    }
}
