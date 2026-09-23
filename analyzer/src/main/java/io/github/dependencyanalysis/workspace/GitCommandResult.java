package io.github.dependencyanalysis.workspace;

/**
 * Immutable result of a git command execution.
 */
public final class GitCommandResult {

    /** Process exit code. */
    private final int exitCode;

    /** Captured standard output. */
    private final String stdout;

    private GitCommandResult(
            final int code,
            final String out) {
        this.exitCode = code;
        this.stdout = out;
    }

    /**
     * Creates a new result.
     *
     * @param exitCode process exit code
     * @param stdout   captured stdout
     * @return new instance
     */
    public static GitCommandResult of(
            final int exitCode,
            final String stdout) {
        return new GitCommandResult(
                exitCode, stdout);
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

    @Override
    public String toString() {
        return "GitCommandResult{"
                + "exitCode=" + exitCode
                + ", stdout='" + stdout + '\''
                + '}';
    }
}
