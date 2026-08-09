package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * One exact CGNode on a deterministic shortest Call Graph chain.
 *
 * @param node exact CGNode identity
 * @param cycle whether the CGNode belongs to a cycle
 */
public record CallGraphNodePathStep(
        CallGraphNodeIdentity node,
        boolean cycle) {

    /** Validates the CGNode identity. */
    public CallGraphNodePathStep {
        Objects.requireNonNull(node, "node");
    }
}
