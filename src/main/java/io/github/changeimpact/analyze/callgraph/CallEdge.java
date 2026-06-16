package io.github.changeimpact.analyze.callgraph;

import java.util.Objects;

/**
 * Immutable directed call edge from
 * a caller method to a callee method.
 */
public final class CallEdge {

    /** Caller method identifier. */
    private final MethodId caller;

    /** Callee method identifier. */
    private final MethodId callee;

    /** Kind of invocation edge. */
    private final EdgeKind edgeKind;

    /** Evidence describing the call
     *  instruction type. */
    private final String evidence;

    /**
     * Creates a new call edge.
     *
     * @param call     caller method id
     * @param calleeId callee method id
     * @param kind     edge kind
     * @param evid     evidence string
     */
    public CallEdge(
            final MethodId call,
            final MethodId calleeId,
            final EdgeKind kind,
            final String evid) {
        this.caller = Objects.requireNonNull(
                call, "caller");
        this.callee = Objects.requireNonNull(
                calleeId, "callee");
        this.edgeKind = Objects.requireNonNull(
                kind, "edgeKind");
        this.evidence = Objects.requireNonNull(
                evid, "evidence");
    }

    /**
     * Returns the caller method id.
     *
     * @return caller method id
     */
    public MethodId getCaller() {
        return caller;
    }

    /**
     * Returns the callee method id.
     *
     * @return callee method id
     */
    public MethodId getCallee() {
        return callee;
    }

    /**
     * Returns the edge kind.
     *
     * @return edge kind
     */
    public EdgeKind getEdgeKind() {
        return edgeKind;
    }

    /**
     * Returns the evidence string.
     *
     * @return evidence
     */
    public String getEvidence() {
        return evidence;
    }

    @Override
    public boolean equals(
            final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallEdge)) {
            return false;
        }
        final CallEdge that =
                (CallEdge) o;
        return caller.equals(that.caller)
                && callee.equals(that.callee)
                && edgeKind == that.edgeKind
                && evidence.equals(
                        that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                caller, callee,
                edgeKind, evidence);
    }

    @Override
    public String toString() {
        return "CallEdge{"
                + "caller=" + caller
                + ", callee=" + callee
                + ", edgeKind=" + edgeKind
                + ", evidence='"
                + evidence + '\''
                + '}';
    }
}
