package io.github.dependencyanalysis.callgraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable evidence captured while reachable callsites are modeled. */
public final class DynamicCallEvidenceIndex {

    /** Stable captured evidence. */
    private final List<DynamicCallEvidence> evidence;

    DynamicCallEvidenceIndex(final List<DynamicCallEvidence> values) {
        final List<DynamicCallEvidence> sorted = new ArrayList<>(values);
        sorted.sort(Comparator
                .comparing((DynamicCallEvidence value) -> value.caller()
                        .getMethod().getReference().toString())
                .thenComparingInt(value -> value.caller().getGraphNodeId())
                .thenComparingInt(DynamicCallEvidence::bytecodePc)
                .thenComparing(value -> value.kind().name())
                .thenComparing(DynamicCallEvidence::targetOwner)
                .thenComparing(DynamicCallEvidence::targetName)
                .thenComparing(DynamicCallEvidence::targetDescriptor));
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

    /** @return all stable evidence */
    public List<DynamicCallEvidence> all() {
        return evidence;
    }
}
