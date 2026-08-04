package io.github.dependencyanalysis.impact;

import java.util.Objects;

/** SSA equivalence status plus stable diagnostic reason. */
public final class MethodEquivalenceResult {

    /** Comparison status. */
    private final MethodEquivalenceStatus status;

    /** Diagnostic reason. */
    private final String reason;

    /**
     * Creates a comparison result.
     *
     * @param value status
     * @param detail reason
     */
    public MethodEquivalenceResult(
            final MethodEquivalenceStatus value,
            final String detail) {
        status = Objects.requireNonNull(value, "status");
        reason = Objects.requireNonNull(detail, "reason");
    }

    /** @return comparison status */
    public MethodEquivalenceStatus getStatus() {
        return status;
    }

    /** @return stable diagnostic reason */
    public String getReason() {
        return reason;
    }
}
