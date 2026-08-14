package io.github.dependencyanalysis.callgraph.protocol.invokedynamic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable evidence captured while reachable callsites are modeled. */
public final class DynamicCallEvidenceIndex {

    /** Stable captured evidence. */
    private final List<DynamicCallEvidence> evidence;

    /**
     * Creates a stable evidence index.
     *
     * @param values decoded evidence
     */
    public DynamicCallEvidenceIndex(final List<DynamicCallEvidence> values) {
        final List<DynamicCallEvidence> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(DynamicCallEvidence::stableKey));
        evidence = List.copyOf(sorted);
    }

    /**
     * Finds matching method-handle or bootstrap references.
     *
     * @param owner internal owner
     * @param name method name
     * @param descriptor JVM descriptor
     * @return stable matching evidence
     */
    public List<DynamicCallEvidence> find(
            final String owner,
            final String name,
            final String descriptor) {
        return evidence.stream()
                .filter(value -> owner.equals(value.targetOwner()))
                .filter(value -> name.equals(value.targetName()))
                .filter(value -> descriptor.equals(
                        value.targetDescriptor()))
                .toList();
    }

    /**
     * Finds all dynamic references whose target belongs to one class.
     *
     * @param owner internal owner
     * @return stable matching evidence
     */
    public List<DynamicCallEvidence> findOwner(final String owner) {
        return evidence.stream()
                .filter(value -> owner.equals(value.targetOwner()))
                .toList();
    }

    /** @return all stable evidence */
    public List<DynamicCallEvidence> all() {
        return evidence;
    }
}
