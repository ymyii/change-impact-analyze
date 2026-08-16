package io.github.dependencyanalysis.dependency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable include/exclude boundary for changed Maven JAR sources. */
public final class DependencyArtifactSelection {

    /** Inclusive Maven artifact patterns. */
    private final List<MavenArtifactPattern> includes;

    /** Exclusive Maven artifact patterns. */
    private final List<MavenArtifactPattern> excludes;

    private DependencyArtifactSelection(
            final List<MavenArtifactPattern> included,
            final List<MavenArtifactPattern> excluded) {
        includes = List.copyOf(included);
        excludes = List.copyOf(excluded);
    }

    /** @return default unfiltered dependency selection */
    public static DependencyArtifactSelection allDependencies() {
        return new DependencyArtifactSelection(List.of(), List.of());
    }

    /**
     * Parses repeatable CLI selector values.
     *
     * @param included include expressions
     * @param excluded exclude expressions
     * @return validated selection with stable duplicate removal
     */
    public static DependencyArtifactSelection parse(
            final List<String> included,
            final List<String> excluded) {
        Objects.requireNonNull(included, "included");
        Objects.requireNonNull(excluded, "excluded");
        return new DependencyArtifactSelection(
                patterns(included), patterns(excluded));
    }

    private static List<MavenArtifactPattern> patterns(
            final List<String> values) {
        final LinkedHashSet<MavenArtifactPattern> result =
                new LinkedHashSet<>();
        values.stream().map(MavenArtifactPattern::parse)
                .forEach(result::add);
        return List.copyOf(result);
    }

    /**
     * @param artifact Maven artifact coordinate
     * @return true when the changed artifact is selected
     */
    public boolean matches(final ArtifactCoord artifact) {
        final boolean included = includes.isEmpty()
                || includes.stream().anyMatch(value ->
                value.matches(artifact));
        return included && excludes.stream().noneMatch(value ->
                value.matches(artifact));
    }

    /** @return whether the user supplied at least one selector */
    public boolean isFiltered() {
        return !includes.isEmpty() || !excludes.isEmpty();
    }

    /** @return immutable include patterns */
    public List<MavenArtifactPattern> includes() {
        return includes;
    }

    /** @return immutable exclude patterns */
    public List<MavenArtifactPattern> excludes() {
        return excludes;
    }

    @Override
    public String toString() {
        return "include=" + includes + "; exclude=" + excludes;
    }
}
