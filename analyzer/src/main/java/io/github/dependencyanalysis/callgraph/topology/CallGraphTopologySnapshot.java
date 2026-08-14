package io.github.dependencyanalysis.callgraph.topology;

import java.util.List;

/**
 * Read-only CGNode topology captured from the completed Call Graph.
 *
 * @param entrypointCount declared entrypoint CGNode count
 * @param nodeCount raw Call Graph node count
 * @param edgeCount raw Call Graph edge count
 * @param topCallers top callers by related callee CGNode count
 * @param topCallees top callees by related caller CGNode count
 */
public record CallGraphTopologySnapshot(
        int entrypointCount,
        int nodeCount,
        int edgeCount,
        List<CallGraphRankedNode> topCallers,
        List<CallGraphRankedNode> topCallees) {

    /** Defensively copies ranked diagnostics. */
    public CallGraphTopologySnapshot {
        topCallers = List.copyOf(topCallers);
        topCallees = List.copyOf(topCallees);
    }
}
