package io.github.dependencyanalysis.callgraph.topology;

import java.util.Objects;

/**
 * Exact WALA CGNode identity used by benchmark diagnostics.
 *
 * @param graphNodeId WALA graph-local node identifier
 * @param method context-independent Method identity
 * @param context stable printable WALA Context
 * @param walaSynthetic whether WALA generated or summarized the Method
 * @param sentinelRole WALA sentinel role for this exact node
 */
public record CallGraphNodeIdentity(
        int graphNodeId,
        CallGraphMethodIdentity method,
        String context,
        boolean walaSynthetic,
        CallGraphNodeSentinelRole sentinelRole)
        implements Comparable<CallGraphNodeIdentity> {

    /** Validates required identity parts. */
    public CallGraphNodeIdentity {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(sentinelRole, "sentinelRole");
    }

    /** @return deterministic identity including Context and graph node id */
    public String stableKey() {
        return method.stableKey() + "|context=" + context
                + "|nodeId=" + graphNodeId
                + "|walaSynthetic=" + walaSynthetic
                + "|sentinelRole=" + sentinelRole;
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
        if (result == 0) {
            result = sentinelRole.compareTo(other.sentinelRole);
        }
        return result;
    }
}
