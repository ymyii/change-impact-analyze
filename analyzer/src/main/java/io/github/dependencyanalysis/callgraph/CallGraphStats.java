package io.github.dependencyanalysis.callgraph;

/**
 * Immutable statistics about a call
 * graph construction run.
 *
 * @param methodCount              number
 *                                 of methods
 * @param edgeCount                number
 *                                 of edges
 * @param elapsedMillis            elapsed
 *                                 millis
 * @param memoryHighWatermarkBytes peak
 *                                 memory
 */
public record CallGraphStats(
        int methodCount,
        int edgeCount,
        long elapsedMillis,
        long memoryHighWatermarkBytes) {

    /**
     * Compact constructor with
     * validation.
     */
    public CallGraphStats {
        if (methodCount < 0) {
            throw new IllegalArgumentException(
                    "methodCount < 0");
        }
        if (edgeCount < 0) {
            throw new IllegalArgumentException(
                    "edgeCount < 0");
        }
    }
}
