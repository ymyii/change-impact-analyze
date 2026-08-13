package io.github.dependencyanalysis.callgraph;

/** Builds one Call Graph using one fixed algorithm implementation. */
interface CallGraphAlgorithmStrategy {

    /** @return algorithm implemented by this strategy */
    CallGraphAlgorithm algorithm();

    /** @return immutable strategy capability declaration */
    CallGraphStrategyCapabilities capabilities();

    /**
     * Builds the graph and snapshots all model metadata.
     *
     * @param request immutable build input
     * @return completed graph and immutable metadata
     * @throws Exception when WALA cannot complete the fixed point
     */
    CallGraphStrategyResult build(CallGraphBuildRequest request)
            throws Exception;
}
