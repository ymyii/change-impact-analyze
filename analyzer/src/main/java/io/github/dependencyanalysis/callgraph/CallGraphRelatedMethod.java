package io.github.dependencyanalysis.callgraph;

import java.util.List;
import java.util.Objects;

/**
 * IMethod-aggregated child ranking for one exact caller or callee CGNode.
 *
 * @param method related Method identity
 * @param rawEdgeCount raw Context edge count for this Method
 * @param relatedNodes exact related CGNodes represented by the Method
 */
public record CallGraphRelatedMethod(
        CallGraphMethodIdentity method,
        int rawEdgeCount,
        List<CallGraphNodeIdentity> relatedNodes) {

    /** Validates and copies the child ranking. */
    public CallGraphRelatedMethod {
        Objects.requireNonNull(method, "method");
        relatedNodes = List.copyOf(relatedNodes);
    }

    /** @return related CGNode count for this Method */
    public int relatedCgNodeCount() {
        return relatedNodes.size();
    }
}
