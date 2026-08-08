package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CallGraph;

import java.util.Objects;

/**
 * Completed algorithm-specific fixed-point output.
 *
 * @param graph completed Call Graph
 * @param metadata immutable model snapshot
 */
record CallGraphStrategyResult(
        CallGraph graph,
        StrategyModelMetadata metadata) {

    CallGraphStrategyResult {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(metadata, "metadata");
    }
}
