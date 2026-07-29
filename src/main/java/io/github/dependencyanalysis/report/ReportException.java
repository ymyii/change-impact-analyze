package io.github.dependencyanalysis.report;

/**
 * Runtime exception for report
 * generation failures.
 */
public class ReportException
        extends RuntimeException {

    /** Serial version UID. */
    private static final long
            SERIAL_VERSION = 1L;

    /**
     * Creates a new report
     * exception.
     *
     * @param message error message
     */
    public ReportException(
            final String message) {
        super(message);
    }

    /**
     * Creates a new report
     * exception with cause.
     *
     * @param message error message
     * @param cause   root cause
     */
    public ReportException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
