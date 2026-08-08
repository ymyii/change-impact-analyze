package io.github.dependencyanalysis.impact;

import java.util.List;
import java.util.Objects;

/**
 * Typed seed resolution with aggregate reference observation.
 *
 * @param seeds impacting graph seeds
 * @param observation aggregate reference observation
 * @param evidence typed reference evidence
 * @param limitations query coverage limitations
 */
record ChangePointSeedResolution(
        List<ImpactSeed> seeds,
        ReferenceObservation observation,
        List<ImpactEvidence> evidence,
        List<QueryLimitation> limitations) {

    ChangePointSeedResolution {
        seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds"));
        Objects.requireNonNull(observation, "observation");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        limitations = List.copyOf(Objects.requireNonNull(
                limitations, "limitations"));
        if (observation == ReferenceObservation.NONE
                && (!seeds.isEmpty() || !evidence.isEmpty())) {
            throw new IllegalArgumentException(
                    "NONE resolution cannot retain reference evidence");
        }
        if (observation == ReferenceObservation.ACCESSIBLE_ONLY
                && (!seeds.isEmpty() || evidence.isEmpty()
                || evidence.stream().anyMatch(value ->
                !(value instanceof AccessReferenceEvidence access)
                        || access.decision()
                        != AccessDecision.ACCESSIBLE))) {
            throw new IllegalArgumentException(
                    "ACCESSIBLE_ONLY requires accessible evidence only");
        }
        if (observation == ReferenceObservation.IMPACTING
                && seeds.isEmpty()) {
            throw new IllegalArgumentException(
                    "IMPACTING resolution requires a seed");
        }
    }
}
