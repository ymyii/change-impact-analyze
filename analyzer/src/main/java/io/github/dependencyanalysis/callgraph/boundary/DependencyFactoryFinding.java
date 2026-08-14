package io.github.dependencyanalysis.callgraph.boundary;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Immutable caller-specific flow-to-cast factory finding.
 *
 * @param callerMethod caller method identity
 * @param resolvedCallee resolved external callee
 * @param calleeArtifact external artifact
 * @param bytecodePc caller bytecode program counter
 * @param castInstruction cast instruction index
 * @param inferredType inferred concrete result type
 * @param context caller context
 */
public record DependencyFactoryFinding(
        String callerMethod,
        String resolvedCallee,
        ArtifactCoord calleeArtifact,
        int bytecodePc,
        int castInstruction,
        String inferredType,
        String context) implements Comparable<DependencyFactoryFinding> {

    /** Stable machine-readable finding code. */
    public static final String CODE = "FLOW_TO_CAST_FACTORY";

    /** Validates the finding. */
    public DependencyFactoryFinding {
        Objects.requireNonNull(callerMethod, "callerMethod");
        Objects.requireNonNull(resolvedCallee, "resolvedCallee");
        Objects.requireNonNull(calleeArtifact, "calleeArtifact");
        Objects.requireNonNull(inferredType, "inferredType");
        Objects.requireNonNull(context, "context");
    }

    /** @return deterministic identity */
    public String stableKey() {
        return CODE + "|" + callerMethod + "|" + bytecodePc + "|"
                + resolvedCallee + "|" + castInstruction + "|"
                + inferredType + "|" + context;
    }

    /** @return stable diagnostic summary */
    public String summary() {
        return CODE + ": caller=" + callerMethod + "; pc=" + bytecodePc
                + "; callee=" + resolvedCallee + "; cast="
                + castInstruction + "; inferredType=" + inferredType;
    }

    @Override
    public int compareTo(final DependencyFactoryFinding other) {
        return stableKey().compareTo(other.stableKey());
    }
}
