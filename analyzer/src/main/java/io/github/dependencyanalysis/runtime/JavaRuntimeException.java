package io.github.dependencyanalysis.runtime;

/** Failure while resolving the user project JDK. */
public final class JavaRuntimeException
        extends RuntimeException {

    /**
     * Creates an exception.
     *
     * @param message failure message
     */
    public JavaRuntimeException(
            final String message) {
        super(message);
    }

    /**
     * Creates an exception with cause.
     *
     * @param message failure message
     * @param cause failure cause
     */
    public JavaRuntimeException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
