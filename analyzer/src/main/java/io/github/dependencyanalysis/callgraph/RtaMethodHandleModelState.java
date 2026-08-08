package io.github.dependencyanalysis.callgraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

/** Mutable RTA MethodHandle build state snapshotted after fixed point. */
final class RtaMethodHandleModelState {

    /** Direct modeled targets. */
    private final Map<String, DynamicCallEvidence> evidence =
            new LinkedHashMap<>();

    /** Stable local-model limitations. */
    private final Set<ModelLimitation> limitations = new LinkedHashSet<>();

    void addEvidence(final DynamicCallEvidence value) {
        evidence.putIfAbsent(value.stableKey(), value);
    }

    void addLimitation(
            final String location,
            final String detail) {
        limitations.add(new ModelLimitation(ModelKind.METHOD_HANDLE,
                "RTA_METHOD_HANDLE_LOCAL_TARGET_UNRESOLVED",
                ModuleAnalysisReason.INCONCLUSIVE_METHOD_HANDLE_MODEL,
                location, detail));
    }

    List<DynamicCallEvidence> evidence() {
        return new ArrayList<>(evidence.values());
    }

    List<ModelLimitation> limitations() {
        return limitations.stream().sorted().toList();
    }
}
