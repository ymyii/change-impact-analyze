package io.github.dependencyanalysis.callgraph.topology;

import java.util.Objects;

/**
 * One exact CGNode on a deterministic shortest Call Graph chain.
 *
 * @param node exact CGNode identity
 * @param cycle whether the CGNode belongs to a cycle
 *
 * <p>The node identity carries its WALA sentinel role so fake root and
 * fake world-clinit remain explicit in exported chains.</p>
 */
public record CallGraphNodePathStep(
        CallGraphNodeIdentity node,
        boolean cycle) {

    /** Validates the CGNode identity. */
    public CallGraphNodePathStep {
        Objects.requireNonNull(node, "node");
    }
}
