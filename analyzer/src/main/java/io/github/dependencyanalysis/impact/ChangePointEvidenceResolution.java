package io.github.dependencyanalysis.impact;

import java.util.List;
import java.util.Objects;

/**
 * Frozen evidence outcome for one bound change.
 *
 * @param changePoint bound change
 * @param status aggregate outcome
 * @param evidence stable evidence list
 * @param limitations typed coverage limitations
 */
public record ChangePointEvidenceResolution(
        BoundChangePoint changePoint,
        EvidenceResolutionStatus status,
        List<ReferenceEvidence> evidence,
        List<CoverageLimitation> limitations) {

    /** Validates and orders the resolution. */
    public ChangePointEvidenceResolution {
        Objects.requireNonNull(changePoint, "changePoint");
        Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence").stream()
                .distinct().sorted().toList();
        limitations = Objects.requireNonNull(limitations, "limitations")
                .stream().distinct().sorted(java.util.Comparator.comparing(
                        CoverageLimitation::stableKey)).toList();
        if (status == EvidenceResolutionStatus.MATCHED
                && evidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "MATCHED evidence resolution requires evidence");
        }
    }
}
