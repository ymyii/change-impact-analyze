package io.github.dependencyanalysis.runtime;

/** Captured Maven process result. */
public final class MavenExecutionResult {

    /** Exit code. */
    private final int exitCode;

    /** Standard output. */
    private final String standardOutput;

    /** Standard error. */
    private final String standardError;

    /**
     * Creates a process result.
     *
     * @param code process exit code
     * @param stdout standard output
     * @param stderr standard error
     */
    public MavenExecutionResult(
            final int code,
            final String stdout,
            final String stderr) {
        exitCode = code;
        standardOutput = stdout;
        standardError = stderr;
    }

    /** @return process exit code */
    public int getExitCode() {
        return exitCode;
    }

    /** @return standard output */
    public String getStandardOutput() {
        return standardOutput;
    }

    /** @return standard error */
    public String getStandardError() {
        return standardError;
    }

    /** @return combined output */
    public String getCombinedOutput() {
        return standardOutput + "\n" + standardError;
    }
}
