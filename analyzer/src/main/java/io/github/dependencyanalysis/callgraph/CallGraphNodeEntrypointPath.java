package io.github.dependencyanalysis.callgraph;

import java.util.List;
import java.util.Objects;

/**
 * One reachable declared entrypoint CGNode and its shortest node chain.
 *
 * @param entrypoint declared entrypoint CGNode
 * @param steps deterministic shortest CGNode chain
 */
public record CallGraphNodeEntrypointPath(
        CallGraphNodeIdentity entrypoint,
        List<CallGraphNodePathStep> steps) {

    /** Defensively copies the path. */
    public CallGraphNodeEntrypointPath {
        Objects.requireNonNull(entrypoint, "entrypoint");
        steps = List.copyOf(steps);
    }
}
