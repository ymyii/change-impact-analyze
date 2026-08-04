package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.EdgeKind;

import java.util.Objects;

/** Synthetic path terminal representing one BoundChangePoint. */
public final class ChangePointTerminal {

    /** Bound change. */
    private final BoundChangePoint changePoint;

    /** Terminal evidence kind. */
    private final EdgeKind edgeKind;

    /** Stable evidence. */
    private final String evidence;

    /**
     * Creates a terminal.
     *
     * @param point bound change
     * @param kind terminal edge kind
     * @param detail stable evidence
     */
    public ChangePointTerminal(
            final BoundChangePoint point,
            final EdgeKind kind,
            final String detail) {
        changePoint = Objects.requireNonNull(point, "changePoint");
        edgeKind = Objects.requireNonNull(kind, "edgeKind");
        evidence = Objects.requireNonNull(detail, "evidence");
    }

    /** @return bound ChangePoint */
    public BoundChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return terminal edge kind */
    public EdgeKind getEdgeKind() {
        return edgeKind;
    }

    /** @return stable evidence */
    public String getEvidence() {
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
                && edgeKind == that.edgeKind
                && evidence.equals(that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changePoint, edgeKind, evidence);
    }
}
