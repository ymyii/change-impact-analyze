package io.github.dependencyanalysis.runtime;

/** Maven runtime preparation failure. */
public final class MavenRuntimeException
        extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message failure message
     */
    public MavenRuntimeException(
            final String message) {
        super(message);
    }

    /**
     * Creates an exception with cause.
     *
     * @param message failure message
     * @param cause failure cause
     */
    public MavenRuntimeException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
