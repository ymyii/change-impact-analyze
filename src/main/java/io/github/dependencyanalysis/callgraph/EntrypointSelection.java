package io.github.dependencyanalysis.callgraph;

import java.util.List;
import java.util.Objects;

/** Immutable include/exclude boundary for PROJECT entrypoint classes. */
public final class EntrypointSelection {

    /** Inclusive patterns. */
    private final List<EntrypointPattern> includes;

    /** Exclusive patterns. */
    private final List<EntrypointPattern> excludes;

    private EntrypointSelection(
            final List<EntrypointPattern> included,
            final List<EntrypointPattern> excluded) {
        includes = List.copyOf(included);
        excludes = List.copyOf(excluded);
    }

    /** @return default unfiltered selection */
    public static EntrypointSelection allProjectClasses() {
        return new EntrypointSelection(List.of(), List.of());
    }

    /**
     * Parses repeatable CLI selector values.
     *
     * @param included include expressions
     * @param excluded exclude expressions
     * @return validated selection
     */
    public static EntrypointSelection parse(
            final List<String> included,
            final List<String> excluded) {
        Objects.requireNonNull(included, "included");
        Objects.requireNonNull(excluded, "excluded");
        return new EntrypointSelection(
                included.stream().map(EntrypointPattern::parse).toList(),
                excluded.stream().map(EntrypointPattern::parse).toList());
    }

    /** @return whether the user supplied at least one selector */
    public boolean isFiltered() {
        return !includes.isEmpty() || !excludes.isEmpty();
    }

    /**
     * Tests a PROJECT class against the configured boundary.
     *
     * @param internalName internal JVM class name
     * @return true when the PROJECT class is selected
     */
    public boolean matchesInternalName(final String internalName) {
        final boolean included = includes.isEmpty()
                || includes.stream().anyMatch(value ->
                value.matchesInternalName(internalName));
        return included && excludes.stream().noneMatch(value ->
                value.matchesInternalName(internalName));
    }

    /** @return include patterns */
    public List<EntrypointPattern> includes() {
        return includes;
    }

    /** @return exclude patterns */
    public List<EntrypointPattern> excludes() {
        return excludes;
    }
}
