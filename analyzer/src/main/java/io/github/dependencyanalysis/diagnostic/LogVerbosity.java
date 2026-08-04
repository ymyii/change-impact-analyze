package io.github.dependencyanalysis.diagnostic;

/** Console diagnostic verbosity selected by the CLI. */
public enum LogVerbosity {

    /** Operational progress, warnings, and errors. */
    INFO,

    /** Additional analysis decisions and configuration. */
    DEBUG,

    /** Fine-grained analysis evidence. */
    TRACE;

    /**
     * Tests whether this verbosity includes the requested detail.
     *
     * @param requested requested detail level
     * @return true when the detail is enabled
     */
    public boolean includes(final LogVerbosity requested) {
        return ordinal() >= requested.ordinal();
    }

    /**
     * Maps repeated {@code -v} flags to a verbosity.
     *
     * @param count number of flags
     * @return INFO, DEBUG, or TRACE
     */
    public static LogVerbosity fromVerboseCount(final int count) {
        if (count <= 0) {
            return INFO;
        }
        if (count == 1) {
            return DEBUG;
        }
        return TRACE;
    }
}
