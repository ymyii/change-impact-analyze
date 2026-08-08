package io.github.dependencyanalysis.models.jdk;

import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic usage metadata for one installed model session.
 *
 * @param modelId stable model identifier
 * @param catalogTargetCount committed catalog target count
 * @param availableTargetCount targets resolved in the active hierarchy
 * @param unavailableTargetCount targets unavailable in the hierarchy
 * @param hitTargetCount targets selected during Call Graph construction
 * @param availableTargets stable available target identities
 * @param unavailableTargets stable unavailable target identities
 * @param hitTargets stable selected target identities
 */
public record JdkModelMetadata(
        String modelId,
        int catalogTargetCount,
        int availableTargetCount,
        int unavailableTargetCount,
        int hitTargetCount,
        List<String> availableTargets,
        List<String> unavailableTargets,
        List<String> hitTargets) {

    /** Validates and snapshots model metadata. */
    public JdkModelMetadata {
        modelId = Objects.requireNonNull(modelId, "modelId");
        availableTargets = List.copyOf(Objects.requireNonNull(
                availableTargets, "availableTargets"));
        unavailableTargets = List.copyOf(Objects.requireNonNull(
                unavailableTargets, "unavailableTargets"));
        hitTargets = List.copyOf(Objects.requireNonNull(
                hitTargets, "hitTargets"));
        if (catalogTargetCount < 0 || availableTargetCount < 0
                || unavailableTargetCount < 0 || hitTargetCount < 0) {
            throw new IllegalArgumentException(
                    "Model counts must be non-negative");
        }
        if (catalogTargetCount
                != availableTargetCount + unavailableTargetCount) {
            throw new IllegalArgumentException(
                    "Catalog count must equal available plus unavailable");
        }
        if (availableTargetCount != availableTargets.size()
                || unavailableTargetCount != unavailableTargets.size()
                || hitTargetCount != hitTargets.size()) {
            throw new IllegalArgumentException(
                    "Model counts must match their target lists");
        }
    }
}
