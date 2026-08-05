package io.github.dependencyanalysis.impact;

import java.util.List;

/** Immutable structural evidence collected before Call Graph fixed point. */
public final class StructuralScanResult {

    /** All module-scope structural references. */
    private final List<StructuralReferenceMatch> references;

    StructuralScanResult(final List<StructuralReferenceMatch> values) {
        references = List.copyOf(values);
    }

    /** @return empty scan result */
    public static StructuralScanResult empty() {
        return new StructuralScanResult(List.of());
    }

    /** @return stable structural references */
    public List<StructuralReferenceMatch> references() {
        return references;
    }
}
