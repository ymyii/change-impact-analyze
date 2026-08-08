package io.github.dependencyanalysis.callgraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

/** Mutable build-time registry state snapshotted after fixed point. */
final class InvokeDynamicModelState {

    /** Deduplicated evidence. */
    private final Map<String, DynamicCallEvidence> evidence =
            new LinkedHashMap<>();

    /** Stable limitations. */
    private final Set<ModelLimitation> limitations = new LinkedHashSet<>();

    void addEvidence(final DynamicCallEvidence value) {
        evidence.putIfAbsent(value.stableKey(), value);
    }

    void addLimitation(
            final String code,
            final String location,
            final String detail) {
        addLimitation(new ModelLimitation(ModelKind.INVOKEDYNAMIC,
                code, ModuleAnalysisReason.INCONCLUSIVE_INVOKEDYNAMIC_MODEL,
                location, detail));
    }

    void addLimitation(final ModelLimitation limitation) {
        limitations.add(limitation);
    }

    DynamicCallEvidenceIndex evidenceIndex() {
        return new DynamicCallEvidenceIndex(
                new ArrayList<>(evidence.values()));
    }

    List<ModelLimitation> limitations() {
        return limitations.stream().sorted().toList();
    }
}
