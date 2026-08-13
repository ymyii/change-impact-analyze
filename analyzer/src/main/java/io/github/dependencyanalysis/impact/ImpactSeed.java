package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Exact graph seed and typed terminal evidence.
 *
 * @param node exact query node
 * @param evidence typed terminal evidence
 */
record ImpactSeed(
        QueryNode node,
        ReferenceEvidence evidence) {

    ImpactSeed {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(evidence, "evidence");
    }
}
