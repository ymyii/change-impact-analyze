package io.github.dependencyanalysis.impact;

/**
 * Runtime exception thrown when
 * impact tracing encounters an
 * unrecoverable error.
 */
public class ImpactException
        extends RuntimeException {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /**
     * Creates with message.
     *
     * @param msg detail message
     */
    public ImpactException(
            final String msg) {
        super(msg);
    }

    /**
     * Creates with message and
     * root cause.
     *
     * @param msg   detail message
     * @param cause root cause
     */
    public ImpactException(
            final String msg,
            final Throwable cause) {
        super(msg, cause);
    }
}
