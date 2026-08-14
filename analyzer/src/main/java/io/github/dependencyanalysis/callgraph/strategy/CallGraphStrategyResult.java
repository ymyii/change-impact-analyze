package io.github.dependencyanalysis.callgraph.strategy;

import com.ibm.wala.ipa.callgraph.CallGraph;

import java.util.Objects;

/**
 * Completed algorithm-specific fixed-point output.
 *
 * @param graph completed Call Graph
 * @param metadata immutable model snapshot
 */
public record CallGraphStrategyResult(
        CallGraph graph,
        StrategyModelMetadata metadata) {

    /** Validates the completed strategy output. */
    public CallGraphStrategyResult {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(metadata, "metadata");
    }
}
