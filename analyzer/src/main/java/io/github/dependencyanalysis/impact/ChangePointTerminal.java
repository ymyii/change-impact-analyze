package io.github.dependencyanalysis.impact;

import java.util.Objects;

/** Synthetic path terminal representing one BoundChangePoint. */
public final class ChangePointTerminal {

    /** Bound change. */
    private final BoundChangePoint changePoint;

    /** Stable evidence. */
    private final ReferenceEvidence evidence;

    /**
     * Creates a terminal with typed evidence.
     *
     * @param point bound change
     * @param detail typed evidence
     */
    public ChangePointTerminal(
            final BoundChangePoint point,
            final ReferenceEvidence detail) {
        changePoint = Objects.requireNonNull(point, "changePoint");
        evidence = Objects.requireNonNull(detail, "evidence");
    }

    /** @return bound ChangePoint */
    public BoundChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return terminal evidence kind */
    public EvidenceKind getEvidenceKind() {
        return evidence.kind();
    }

    /** @return terminal discovery mechanism */
    public EvidenceMechanism getEvidenceMechanism() {
        return evidence.mechanism();
    }

    /** @return stable evidence */
    public String getEvidence() {
        return evidence.render();
    }

    /** @return typed evidence */
    public ReferenceEvidence getImpactEvidence() {
        return evidence;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangePointTerminal)) {
            return false;
        }
        final ChangePointTerminal that = (ChangePointTerminal) other;
        return changePoint.equals(that.changePoint)
                && evidence.equals(that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changePoint, evidence);
    }
}
