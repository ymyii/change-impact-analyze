package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Frozen Evidence anchor retaining identity without live WALA objects.
 *
 * @param stableKey original live anchor identity
 */
public record StableEvidenceAnchor(String stableKey)
        implements EvidenceAnchor {

    /** Validates stable identity. */
    public StableEvidenceAnchor {
        Objects.requireNonNull(stableKey, "stableKey");
    }
}
