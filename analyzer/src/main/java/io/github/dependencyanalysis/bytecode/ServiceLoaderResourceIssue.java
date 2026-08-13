package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Non-fatal ServiceLoader resource Diff coverage issue.
 *
 * @param code stable issue code
 * @param location artifact/resource location
 * @param detail diagnostic detail
 */
public record ServiceLoaderResourceIssue(
        String code,
        String location,
        String detail) implements Comparable<ServiceLoaderResourceIssue> {

    /** Validates stable diagnostic fields. */
    public ServiceLoaderResourceIssue {
        requireText(code, "code");
        requireText(location, "location");
        requireText(detail, "detail");
    }

    /** @return deterministic identity */
    public String stableKey() {
        return code + "|" + location + "|" + detail;
    }

    @Override
    public int compareTo(final ServiceLoaderResourceIssue other) {
        return stableKey().compareTo(other.stableKey());
    }

    private static void requireText(
            final String value, final String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
