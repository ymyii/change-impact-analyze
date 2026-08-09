package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Source evidence reused by benchmark CGNodes that share one Method.
 *
 * @param status source availability
 * @param source Java-like or ASM text
 * @param reason fallback or unavailability reason
 * @param sha256 SHA-256 of source text, empty when unavailable
 * @param classpathSource exact classpath source label
 */
public record CallGraphMethodSource(
        CallGraphMethodSourceStatus status,
        String source,
        String reason,
        String sha256,
        String classpathSource) {

    /** Validates source evidence fields. */
    public CallGraphMethodSource {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(classpathSource, "classpathSource");
    }
}
