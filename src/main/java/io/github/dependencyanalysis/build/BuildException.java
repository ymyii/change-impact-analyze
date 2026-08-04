package io.github.dependencyanalysis.build;

/**
 * Thrown when a Maven build fails.
 * Carries diagnostic fields for reporting.
 */
public class BuildException
        extends Exception {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /** Side that failed. */
    private final String side;

    /** Module that failed. */
    private final String module;

    /** Command that was executed. */
    private final String command;

    /** Process exit code. */
    private final int exitCode;

    /** Combined process output tail. */
    private final String stderr;

    /**
     * Creates a new build exception.
     *
     * @param s    side that failed
     * @param mod  module path string
     * @param cmd  command executed
     * @param code exit code
     * @param err  combined process output tail
     */
    public BuildException(
            final String s,
            final String mod,
            final String cmd,
            final int code,
            final String err) {
        super(buildMessage(
                s, mod, cmd, code,
                err));
        this.side = s;
        this.module = mod;
        this.command = cmd;
        this.exitCode = code;
        this.stderr = err;
    }

    /**
     * Returns the side that failed.
     *
     * @return side identifier
     */
    public String getSide() {
        return side;
    }

    /**
     * Returns the module path string.
     *
     * @return module path
     */
    public String getModule() {
        return module;
    }

    /**
     * Returns the command that was run.
     *
     * @return command string
     */
    public String getCommand() {
        return command;
    }

    /**
     * Returns the process exit code.
     *
     * @return exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns the stderr summary.
     *
     * @return stderr text
     */
    public String getStderr() {
        return stderr;
    }

    private static String buildMessage(
            final String s,
            final String mod,
            final String cmd,
            final int code,
            final String err) {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("Build failed")
                .append(": side=")
                .append(s)
                .append(", module=")
                .append(mod)
                .append(", command=")
                .append(cmd)
                .append(", exitCode=")
                .append(code)
                .append(", stderr=")
                .append(err);
        return sb.toString();
    }
}
