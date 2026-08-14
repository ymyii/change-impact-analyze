package io.github.dependencyanalysis.callgraph.protocol.invokedynamic;

import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Mutable build-time registry state snapshotted after fixed point. */
public final class InvokeDynamicModelState {

    /** Deduplicated evidence. */
    private final Map<String, DynamicCallEvidence> evidence =
            new LinkedHashMap<>();

    /** Stable limitations. */
    private final Set<ModelLimitation> limitations = new LinkedHashSet<>();

    /** @param value decoded dynamic evidence */
    public void addEvidence(final DynamicCallEvidence value) {
        evidence.putIfAbsent(value.stableKey(), value);
    }

    /**
     * Adds one invokedynamic limitation.
     *
     * @param code stable limitation code
     * @param location stable callsite location
     * @param detail limitation detail
     */
    public void addLimitation(
            final String code,
            final String location,
            final String detail) {
        addLimitation(new ModelLimitation(ModelKind.INVOKEDYNAMIC,
                code, location, detail));
    }

    /** @param limitation typed limitation */
    public void addLimitation(final ModelLimitation limitation) {
        limitations.add(limitation);
    }

    /** @return immutable evidence index */
    public DynamicCallEvidenceIndex evidenceIndex() {
        return new DynamicCallEvidenceIndex(
                new ArrayList<>(evidence.values()));
    }

    /** @return stable typed limitations */
    public List<ModelLimitation> limitations() {
        return limitations.stream().sorted().toList();
    }
}
