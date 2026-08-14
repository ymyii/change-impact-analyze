package io.github.dependencyanalysis.impact;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable command-wide result-refinement selection. */
public final class ResultRefinementSelection {

    /** Stable empty-selection identifier. */
    public static final String NONE = "none";

    /** Shared empty selection. */
    private static final ResultRefinementSelection EMPTY =
            new ResultRefinementSelection(
                    EnumSet.noneOf(ResultRefinementAlgorithm.class));

    /** Selected algorithms in enum declaration order. */
    private final Set<ResultRefinementAlgorithm> algorithms;

    private ResultRefinementSelection(
            final Collection<ResultRefinementAlgorithm> values) {
        final EnumSet<ResultRefinementAlgorithm> selected = values.isEmpty()
                ? EnumSet.noneOf(ResultRefinementAlgorithm.class)
                : EnumSet.copyOf(values);
        algorithms = Set.copyOf(selected);
    }

    /** @return default empty selection */
    public static ResultRefinementSelection defaultSelection() {
        return EMPTY;
    }

    /**
     * Creates a selection from algorithms.
     *
     * @param values selected algorithms
     * @return immutable selection
     */
    public static ResultRefinementSelection of(
            final ResultRefinementAlgorithm... values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            return EMPTY;
        }
        return new ResultRefinementSelection(Arrays.asList(values));
    }

    /**
     * Parses one comma-separated CLI value.
     *
     * @param value CLI value
     * @return immutable selection
     */
    public static ResultRefinementSelection parse(final String value) {
        final String candidate = Objects.requireNonNull(value, "value");
        if (candidate.isBlank()) {
            throw invalid();
        }
        final String[] tokens = candidate.split(",", -1);
        final EnumSet<ResultRefinementAlgorithm> selected =
                EnumSet.noneOf(ResultRefinementAlgorithm.class);
        boolean none = false;
        for (String raw : tokens) {
            final String token = raw.trim();
            if (token.isEmpty()) {
                throw invalid();
            }
            if (NONE.equalsIgnoreCase(token)) {
                none = true;
            } else {
                selected.add(ResultRefinementAlgorithm.parse(token));
            }
        }
        if (none && !selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "none cannot be combined with result-refinement "
                            + "algorithms");
        }
        if (none) {
            return EMPTY;
        }
        return new ResultRefinementSelection(selected);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "expected a comma-separated selection of: "
                        + ResultRefinementAlgorithm.supportedValues()
                        + ", none");
    }

    /**
     * Tests whether one algorithm is selected.
     *
     * @param algorithm result-refinement algorithm
     * @return whether one algorithm is selected
     */
    public boolean isEnabled(final ResultRefinementAlgorithm algorithm) {
        return algorithms.contains(Objects.requireNonNull(
                algorithm, "algorithm"));
    }

    /** @return selected algorithms in stable execution order */
    public List<ResultRefinementAlgorithm> algorithms() {
        return Arrays.stream(ResultRefinementAlgorithm.values())
                .filter(algorithms::contains).toList();
    }

    /** @return stable identifiers in execution order */
    public List<String> identifiers() {
        return algorithms().stream()
                .map(ResultRefinementAlgorithm::identifier).toList();
    }

    /** @return whether no refinement is selected */
    public boolean isEmpty() {
        return algorithms.isEmpty();
    }

    @Override
    public boolean equals(final Object value) {
        return value instanceof ResultRefinementSelection other
                && algorithms.equals(other.algorithms);
    }

    @Override
    public int hashCode() {
        return algorithms.hashCode();
    }

    @Override
    public String toString() {
        return algorithms.isEmpty() ? NONE : algorithms().stream()
                .map(ResultRefinementAlgorithm::identifier)
                .collect(Collectors.joining(","));
    }
}
