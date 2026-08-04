package io.github.dependencyanalysis.workspace;

/**
 * Immutable result of a git command execution.
 */
public final class GitCommandResult {

    /** Process exit code. */
    private final int exitCode;

    /** Captured standard output. */
    private final String stdout;

    /** Captured standard error. */
    private final String stderr;

    private GitCommandResult(
            final int code,
            final String out,
            final String err) {
        this.exitCode = code;
        this.stdout = out;
        this.stderr = err;
    }

    /**
     * Creates a new result.
     *
     * @param exitCode process exit code
     * @param stdout   captured stdout
     * @param stderr   captured stderr
     * @return new instance
     */
    public static GitCommandResult of(
            final int exitCode,
            final String stdout,
            final String stderr) {
        return new GitCommandResult(
                exitCode, stdout, stderr);
    }

    /**
     * Returns the exit code.
     *
     * @return exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns captured stdout.
     *
     * @return stdout string
     */
    public String getStdout() {
        return stdout;
    }

    /**
     * Returns captured stderr.
     *
     * @return stderr string
     */
    public String getStderr() {
        return stderr;
    }

    @Override
    public String toString() {
        return "GitCommandResult{"
                + "exitCode=" + exitCode
                + ", stdout='" + stdout + '\''
                + ", stderr='" + stderr + '\''
                + '}';
    }
}
