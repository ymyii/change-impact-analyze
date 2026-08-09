package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * Exact WALA CGNode identity used by benchmark diagnostics.
 *
 * @param graphNodeId WALA graph-local node identifier
 * @param method context-independent Method identity
 * @param context stable printable WALA Context
 * @param walaSynthetic whether WALA generated or summarized the Method
 */
public record CallGraphNodeIdentity(
        int graphNodeId,
        CallGraphMethodIdentity method,
        String context,
        boolean walaSynthetic) implements Comparable<CallGraphNodeIdentity> {

    /** Validates required identity parts. */
    public CallGraphNodeIdentity {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(context, "context");
    }

    /** @return deterministic identity including Context and graph node id */
    public String stableKey() {
        return method.stableKey() + "|context=" + context
                + "|nodeId=" + graphNodeId
                + "|walaSynthetic=" + walaSynthetic;
    }

    @Override
    public int compareTo(final CallGraphNodeIdentity other) {
        int result = method.compareTo(other.method);
        if (result == 0) {
            result = Integer.compare(graphNodeId, other.graphNodeId);
        }
        if (result == 0) {
            result = context.compareTo(other.context);
        }
        if (result == 0) {
            result = Boolean.compare(walaSynthetic, other.walaSynthetic);
        }
        return result;
    }
}
