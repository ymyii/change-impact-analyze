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

    /**
     * Creates a new dependency analysis
     * exception with structured process
     * failure fields.
     *
     * @param s    side that failed
     * @param mod  module path string
     * @param cmd  command executed
     * @param code exit code
     */
    public DependencyAnalysisException(
            final String s,
            final String mod,
            final String cmd,
            final int code) {
        super(buildMessage(
                s, mod, cmd, code));
        this.side = s;
        this.module = mod;
        this.command = cmd;
        this.exitCode = code;
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

    private static String buildMessage(
            final String s,
            final String mod,
            final String cmd,
            final int code) {
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
                .append(code);
        return sb.toString();
    }
}
