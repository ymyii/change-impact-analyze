package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphNodeSentinelRole;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.MethodId;

import java.util.Objects;

/**
 * Report-safe path node detached from WALA graph and Context objects.
 *
 * @param methodId stable method identity
 * @param origin code origin
 * @param context rendered WALA Context
 * @param graphNodeId original graph node number
 * @param sentinelRole original WALA sentinel role
 */
public record SnapshotQueryNode(
        MethodId methodId,
        CodeOrigin origin,
        String context,
        int graphNodeId,
        CallGraphNodeSentinelRole sentinelRole) implements QueryNode {

    /** Validates the immutable path snapshot. */
    public SnapshotQueryNode {
        Objects.requireNonNull(methodId, "methodId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(sentinelRole, "sentinelRole");
    }
}
