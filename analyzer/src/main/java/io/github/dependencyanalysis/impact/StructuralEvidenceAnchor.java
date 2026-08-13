package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Structural class/member metadata evidence anchor.
 *
 * @param reference exact raw structural fact
 */
public record StructuralEvidenceAnchor(
        StructuralReference reference) implements EvidenceAnchor {

    /** Validates the exact raw structural fact. */
    public StructuralEvidenceAnchor {
        Objects.requireNonNull(reference, "reference");
    }

    @Override
    public String stableKey() {
        return reference.stableKey();
    }
}
