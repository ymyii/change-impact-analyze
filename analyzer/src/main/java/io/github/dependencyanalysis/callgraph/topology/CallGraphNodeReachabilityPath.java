package io.github.dependencyanalysis.callgraph.topology;

import java.util.List;
import java.util.Objects;

/**
 * One reachability root and its deterministic shortest CGNode chain.
 *
 * @param rootKind declared entrypoint or WALA sentinel root kind
 * @param root exact root CGNode
 * @param steps deterministic shortest CGNode chain
 */
public record CallGraphNodeReachabilityPath(
        CallGraphPathRootKind rootKind,
        CallGraphNodeIdentity root,
        List<CallGraphNodePathStep> steps) {

    /** Validates and defensively copies the path. */
    public CallGraphNodeReachabilityPath {
        Objects.requireNonNull(rootKind, "rootKind");
        Objects.requireNonNull(root, "root");
        steps = List.copyOf(steps);
    }
}
