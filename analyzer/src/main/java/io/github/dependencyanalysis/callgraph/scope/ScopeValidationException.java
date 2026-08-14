package io.github.dependencyanalysis.callgraph.scope;

/** Failure raised when a module crosses an excluded JDK boundary. */
public final class ScopeValidationException extends RuntimeException {

    /**
     * Creates a validation failure.
     *
     * @param message failure detail
     */
    public ScopeValidationException(final String message) {
        super(message);
    }

    /**
     * Creates a validation failure with a cause.
     *
     * @param message failure detail
     * @param cause underlying failure
     */
    public ScopeValidationException(
            final String message, final Throwable cause) {
        super(message, cause);
    }
}
