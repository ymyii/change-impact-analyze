package io.github.dependencyanalysis.impact;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable raw structural references collected before target CHA query. */
public final class StructuralReferenceIndex {

    /** Raw symbolic metadata references without ChangePoint binding. */
    private final List<StructuralReference> references;

    StructuralReferenceIndex(final List<StructuralReference> values) {
        references = Objects.requireNonNull(values, "values").stream()
                .distinct()
                .sorted(Comparator.comparing(StructuralReference::stableKey))
                .toList();
    }

    /** @return empty raw index */
    public static StructuralReferenceIndex empty() {
        return new StructuralReferenceIndex(List.of());
    }

    /** @return stable raw structural references */
    public List<StructuralReference> references() {
        return references;
    }
}
