package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.EdgeKind;

import java.util.Objects;

/**
 * Exact graph seed and typed terminal evidence.
 *
 * @param node exact query node
 * @param kind terminal edge kind
 * @param evidence typed terminal evidence
 */
record ImpactSeed(
        QueryNode node,
        EdgeKind kind,
        ImpactEvidence evidence) {

    ImpactSeed {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(evidence, "evidence");
    }
}
