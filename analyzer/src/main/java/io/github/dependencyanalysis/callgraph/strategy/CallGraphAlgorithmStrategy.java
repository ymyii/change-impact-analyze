package io.github.dependencyanalysis.callgraph.strategy;

/** Builds one Call Graph using one fixed algorithm implementation. */
public interface CallGraphAlgorithmStrategy {

    /** @return algorithm implemented by this strategy */
    CallGraphAlgorithm algorithm();

    /** @return immutable strategy capability declaration */
    CallGraphStrategyCapabilities capabilities();

    /**
     * Builds the graph and snapshots all model metadata.
     *
     * @param context algorithm-neutral build context
     * @param request algorithm-specific build request
     * @return completed graph and immutable metadata
     * @throws Exception when WALA cannot complete the fixed point
     */
    CallGraphStrategyResult build(
            CallGraphBuildContext context,
            CallGraphStrategyRequest request)
            throws Exception;
}
