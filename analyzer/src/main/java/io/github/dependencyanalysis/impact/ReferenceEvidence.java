package io.github.dependencyanalysis.impact;

import java.util.Objects;
import java.util.Optional;

/**
 * Algorithm-independent typed ChangePoint terminal evidence.
 *
 * @param anchor reachable method anchor when path traversal applies
 * @param target stable reference target
 * @param kind target category
 * @param mechanism discovery mechanism
 * @param location exact reference location
 * @param detail stable human-readable detail
 */
public record ReferenceEvidence(
        Optional<EvidenceAnchor> anchor,
        ReferenceTarget target,
        EvidenceKind kind,
        EvidenceMechanism mechanism,
        EvidenceLocation location,
        String detail) implements ImpactEvidence,
        Comparable<ReferenceEvidence> {

    /** Validates immutable evidence. */
    public ReferenceEvidence {
        anchor = Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(detail, "detail");
    }

    @Override
    public String stableKey() {
        return kind + "|" + mechanism + "|" + location.stableKey()
                + "|" + target.stableKey() + "|"
                + anchor.map(EvidenceAnchor::stableKey)
                .orElse("NO_METHOD_ANCHOR");
    }

    @Override
    public String render() {
        return "kind=" + kind + "; mechanism=" + mechanism
                + "; target=" + target.stableKey()
                + "; location=" + location.stableKey()
                + "; detail=" + detail;
    }

    @Override
    public int compareTo(final ReferenceEvidence other) {
        return stableKey().compareTo(other.stableKey());
    }
}
