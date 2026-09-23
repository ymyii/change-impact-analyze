package io.github.dependencyanalysis.runtime;

/** Maven exit status and optional stdout return data. */
public final class MavenExecutionResult {

    /** Exit code. */
    private final int exitCode;

    /** Standard output. */
    private final String standardOutput;

    /**
     * Creates a process result.
     *
     * @param code process exit code
     * @param stdout standard output
     */
    public MavenExecutionResult(
            final int code,
            final String stdout) {
        exitCode = code;
        standardOutput = stdout;
    }

    /** @return process exit code */
    public int getExitCode() {
        return exitCode;
    }

    /** @return standard output */
    public String getStandardOutput() {
        return standardOutput;
    }

}
