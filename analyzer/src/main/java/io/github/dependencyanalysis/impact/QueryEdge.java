package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallEdgeKind;

import java.util.Objects;

/** Materialized edge retained only for one representative Impact Path. */
public final class QueryEdge {

    /** Unknown bytecode program counter. */
    public static final int UNKNOWN_PC = -1;

    /** Caller. */
    private final QueryNode caller;

    /** Callee. */
    private final QueryNode callee;

    /** Edge kind. */
    private final CallEdgeKind kind;

    /** Stable evidence. */
    private final String evidence;

    /** Bytecode program counter. */
    private final int bytecodePc;

    /**
     * Creates a materialized path edge.
     *
     * @param source caller
     * @param target callee
     * @param edgeKind edge kind
     * @param detail stable evidence
     * @param pc bytecode PC, or {@link #UNKNOWN_PC}
     */
    public QueryEdge(
            final QueryNode source,
            final QueryNode target,
            final CallEdgeKind edgeKind,
            final String detail,
            final int pc) {
        caller = Objects.requireNonNull(source, "caller");
        callee = Objects.requireNonNull(target, "callee");
        kind = Objects.requireNonNull(edgeKind, "kind");
        evidence = Objects.requireNonNull(detail, "evidence");
        bytecodePc = pc;
    }

    /** @return caller */
    public QueryNode getCaller() {
        return caller;
    }

    /** @return callee */
    public QueryNode getCallee() {
        return callee;
    }

    /** @return edge kind */
    public CallEdgeKind getKind() {
        return kind;
    }

    /** @return stable evidence */
    public String getEvidence() {
        return evidence;
    }

    /** @return bytecode program counter */
    public int getBytecodePc() {
        return bytecodePc;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QueryEdge)) {
            return false;
        }
        final QueryEdge that = (QueryEdge) other;
        return bytecodePc == that.bytecodePc
                && caller.equals(that.caller)
                && callee.equals(that.callee)
                && kind == that.kind
                && evidence.equals(that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caller, callee, kind, evidence, bytecodePc);
    }
}
