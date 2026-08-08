package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Typed coverage limitation produced by target-side impact query.
 *
 * @param code stable machine-readable code
 * @param reason module coverage reason
 * @param location stable ChangePoint/reference location
 * @param detail diagnostic detail
 */
public record QueryLimitation(
        String code,
        ModuleAnalysisReason reason,
        String location,
        String detail) implements CoverageLimitation,
        Comparable<QueryLimitation> {

    /** Validates an immutable query limitation. */
    public QueryLimitation {
        requireText(code, "code");
        Objects.requireNonNull(reason, "reason");
        requireText(location, "location");
        requireText(detail, "detail");
        if (reason != ModuleAnalysisReason.INCONCLUSIVE_SCOPE_VALIDATION) {
            throw new IllegalArgumentException(
                    "Query limitation must use scope validation reason");
        }
    }

    private static void requireText(
            final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public String stableKey() {
        return code + "|" + location + "|" + detail;
    }

    @Override
    public String summary() {
        return code + ": location=" + location + "; detail=" + detail;
    }

    @Override
    public int compareTo(final QueryLimitation other) {
        return stableKey().compareTo(other.stableKey());
    }
}
