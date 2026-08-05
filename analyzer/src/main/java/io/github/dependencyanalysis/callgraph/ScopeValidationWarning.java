package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated excluded JDK references from one external dependency.
 *
 * @param artifact resolved external dependency
 * @param findingCount distinct source/reference findings
 * @param excludedTypeCount distinct excluded JDK types
 * @param examples stable bounded source/reference examples
 */
public record ScopeValidationWarning(
        ArtifactCoord artifact,
        int findingCount,
        int excludedTypeCount,
        List<String> examples) {

    /** Validates and snapshots warning evidence. */
    public ScopeValidationWarning {
        Objects.requireNonNull(artifact, "artifact");
        examples = List.copyOf(Objects.requireNonNull(
                examples, "examples"));
        if (findingCount < 1) {
            throw new IllegalArgumentException(
                    "findingCount must be positive");
        }
        if (excludedTypeCount < 1) {
            throw new IllegalArgumentException(
                    "excludedTypeCount must be positive");
        }
        if (examples.isEmpty() || examples.size() > findingCount) {
            throw new IllegalArgumentException(
                    "examples must represent collected findings");
        }
    }

    /** @return number of findings omitted from the examples */
    public int omittedCount() {
        return findingCount - examples.size();
    }

    /** @return stable warning and coverage-limitation text */
    public String summary() {
        return "External dependency references excluded JDK classes: "
                + "artifact=" + artifact
                + "; findings=" + findingCount
                + "; excludedTypes=" + excludedTypeCount
                + "; examples=[" + String.join(", ", examples) + "]"
                + "; omitted=" + omittedCount()
                + ". Referenced JDK classes remain outside the Call Graph "
                + "scope.";
    }

    /** @return stable deterministic ordering key */
    public String stableKey() {
        return artifact.toString();
    }
}
