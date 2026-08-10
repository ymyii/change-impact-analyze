package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;

import java.util.List;
import java.util.Objects;

/**
 * One complete Module-root-to-changed-dependency occurrence path.
 *
 * @param seed changed target artifact
 * @param occurrences path occurrences without the Module root
 */
public record DependencyPathEvidence(
        ArtifactCoord seed,
        List<ModuleDependencyOccurrenceGraph.Occurrence> occurrences) {

    /** Validates and snapshots path evidence. */
    public DependencyPathEvidence {
        Objects.requireNonNull(seed, "seed");
        occurrences = List.copyOf(Objects.requireNonNull(
                occurrences, "occurrences"));
    }

    /** @return stable human-readable path without Module root */
    public String stablePath() {
        return occurrences.stream().map(value -> value.artifact().toString())
                .reduce((left, right) -> left + " -> " + right).orElse("");
    }
}
