package io.github.dependencyanalysis.callgraph;

/**
 * Thrown when call graph construction
 * fails due to missing class
 * directories or corrupt class files.
 */
public class CallGraphException
        extends RuntimeException {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /**
     * Creates a new call graph
     * exception.
     *
     * @param msg detail message
     */
    public CallGraphException(
            final String msg) {
        super(msg);
    }

    /**
     * Creates a new call graph
     * exception with a cause.
     *
     * @param msg   detail message
     * @param cause root cause
     */
    public CallGraphException(
            final String msg,
            final Throwable cause) {
        super(msg, cause);
    }
}
