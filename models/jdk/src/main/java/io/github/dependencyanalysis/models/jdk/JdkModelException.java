package io.github.dependencyanalysis.models.jdk;

/** Indicates an invalid or unusable JDK Synthetic IR model. */
public final class JdkModelException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a model exception.
     *
     * @param message failure description
     */
    public JdkModelException(final String message) {
        super(message);
    }

    /**
     * Creates a model exception with its cause.
     *
     * @param message failure description
     * @param cause underlying failure
     */
    public JdkModelException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
