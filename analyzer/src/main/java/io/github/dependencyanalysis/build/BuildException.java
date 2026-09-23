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

    /**
     * Creates a new build exception.
     *
     * @param s    side that failed
     * @param mod  module path string
     * @param cmd  command executed
     * @param code exit code
     */
    public BuildException(
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

    private static String buildMessage(
            final String s,
            final String mod,
            final String cmd,
            final int code) {
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
                .append(code);
        return sb.toString();
    }
}
