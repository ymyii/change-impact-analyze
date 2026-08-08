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

    /** Typed failure category. */
    private final CallGraphFailureKind kind;

    /**
     * Creates a new call graph
     * exception.
     *
     * @param msg detail message
     */
    public CallGraphException(
            final String msg) {
        this(CallGraphFailureKind.GENERAL, msg, null);
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
        this(CallGraphFailureKind.GENERAL, msg, cause);
    }

    private CallGraphException(
            final CallGraphFailureKind failureKind,
            final String msg,
            final Throwable cause) {
        super(msg, cause);
        kind = java.util.Objects.requireNonNull(failureKind, "failureKind");
    }

    /**
     * Creates a typed timeout failure.
     *
     * @param msg detail message
     * @return timeout failure
     */
    public static CallGraphException timeout(final String msg) {
        return new CallGraphException(CallGraphFailureKind.TIMEOUT,
                msg, null);
    }

    /**
     * Creates a typed timeout failure with cause.
     *
     * @param msg detail message
     * @param cause root cause
     * @return timeout failure
     */
    public static CallGraphException timeout(
            final String msg,
            final Throwable cause) {
        return new CallGraphException(CallGraphFailureKind.TIMEOUT,
                msg, cause);
    }

    /** @return machine-readable failure category */
    public CallGraphFailureKind getKind() {
        return kind;
    }
}
