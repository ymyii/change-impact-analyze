package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable build-time registry state snapshotted after fixed point. */
final class InvokeDynamicModelState {

    /** Deduplicated evidence. */
    private final Map<String, DynamicCallEvidence> evidence =
            new LinkedHashMap<>();

    /** Stable limitations. */
    private final Set<String> limitations = new LinkedHashSet<>();

    void addEvidence(final DynamicCallEvidence value) {
        final CGNode caller = value.caller();
        final String key = caller.getGraphNodeId() + "|"
                + value.bytecodePc() + "|" + value.kind() + "|"
                + value.targetOwner() + "|" + value.targetName() + "|"
                + value.targetDescriptor();
        evidence.putIfAbsent(key, value);
    }

    void addLimitation(final String value) {
        limitations.add(value);
    }

    DynamicCallEvidenceIndex evidenceIndex() {
        return new DynamicCallEvidenceIndex(
                new ArrayList<>(evidence.values()));
    }

    List<String> limitations() {
        return limitations.stream().sorted().toList();
    }
}
