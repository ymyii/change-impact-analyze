package io.github.dependencyanalysis.dependency;

/**
 * Thrown when dependency analysis fails.
 * Carries diagnostic fields for reporting.
 */
public class DependencyAnalysisException
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
     * Creates a new dependency analysis
     * exception with full process
     * diagnostics.
     *
     * @param s    side that failed
     * @param mod  module path string
     * @param cmd  command executed
     * @param code exit code
     * @param err  combined process output tail
     */
    public DependencyAnalysisException(
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
     * Creates a new dependency analysis
     * exception with a simple message.
     *
     * @param msg error message
     */
    public DependencyAnalysisException(
            final String msg) {
        super(msg);
        this.side = null;
        this.module = null;
        this.command = null;
        this.exitCode = -1;
        this.stderr = null;
    }

    /**
     * Creates a new dependency analysis
     * exception with a message and cause.
     *
     * @param msg   error message
     * @param cause underlying cause
     */
    public DependencyAnalysisException(
            final String msg,
            final Throwable cause) {
        super(msg, cause);
        this.side = null;
        this.module = null;
        this.command = null;
        this.exitCode = -1;
        this.stderr = null;
    }

    /**
     * Returns the side that failed.
     *
     * @return side identifier or null
     */
    public String getSide() {
        return side;
    }

    /**
     * Returns the module path string.
     *
     * @return module path or null
     */
    public String getModule() {
        return module;
    }

    /**
     * Returns the command that was run.
     *
     * @return command string or null
     */
    public String getCommand() {
        return command;
    }

    /**
     * Returns the process exit code.
     *
     * @return exit code or -1
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns the stderr summary.
     *
     * @return stderr text or null
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
        sb.append(
                "Dependency analysis failed")
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
