package io.github.dependencyanalysis.callgraph.topology;

import java.util.List;
import java.util.Objects;

/**
 * Exact caller or callee CGNode benchmark diagnostic.
 *
 * @param node exact ranked CGNode
 * @param relatedCgNodeCount distinct related CGNode count
 * @param distinctRelatedMethodCount distinct related IMethod count
 * @param rawEdgeCount raw edge count in the ranked direction
 * @param topRelatedMethods Top 10 related IMethods by related CGNode count
 * @param cycle whether this exact CGNode belongs to a cycle
 * @param ir WALA IR for this exact CGNode
 * @param reachabilityPaths shortest paths from declared and sentinel roots
 */
public record CallGraphRankedNode(
        CallGraphNodeIdentity node,
        int relatedCgNodeCount,
        int distinctRelatedMethodCount,
        int rawEdgeCount,
        List<CallGraphRelatedMethod> topRelatedMethods,
        boolean cycle,
        CallGraphNodeIr ir,
        List<CallGraphNodeReachabilityPath> reachabilityPaths) {

    /** Validates and copies ranked node values. */
    public CallGraphRankedNode {
        Objects.requireNonNull(node, "node");
        topRelatedMethods = List.copyOf(topRelatedMethods);
        Objects.requireNonNull(ir, "ir");
        reachabilityPaths = List.copyOf(reachabilityPaths);
    }
}
