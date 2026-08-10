package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Typed evidence for a caller-specific flow-to-cast factory summary.
 *
 * @param callerMethod caller method
 * @param resolvedCallee resolved no-op callee
 * @param calleeArtifact logical callee artifact
 * @param bytecodePc invoke bytecode program counter
 * @param castInstruction SSA cast instruction index
 * @param inferredType concrete allocated type
 * @param context caller Context
 */
public record DependencyFactoryEvidence(
        String callerMethod,
        String resolvedCallee,
        ArtifactCoord calleeArtifact,
        int bytecodePc,
        int castInstruction,
        String inferredType,
        String context) implements CoverageLimitation,
        Comparable<DependencyFactoryEvidence> {

    /** Stable machine-readable evidence code. */
    public static final String CODE = "FLOW_TO_CAST_FACTORY";

    /** Validates evidence. */
    public DependencyFactoryEvidence {
        Objects.requireNonNull(callerMethod, "callerMethod");
        Objects.requireNonNull(resolvedCallee, "resolvedCallee");
        Objects.requireNonNull(calleeArtifact, "calleeArtifact");
        Objects.requireNonNull(inferredType, "inferredType");
        Objects.requireNonNull(context, "context");
    }

    @Override
    public ModuleAnalysisReason reason() {
        return ModuleAnalysisReason.INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY;
    }

    @Override
    public String stableKey() {
        return CODE + "|" + callerMethod + "|" + bytecodePc + "|"
                + resolvedCallee + "|" + castInstruction + "|"
                + inferredType + "|" + context;
    }

    @Override
    public String summary() {
        return CODE + ": caller=" + callerMethod + "; pc=" + bytecodePc
                + "; callee=" + resolvedCallee + "; cast="
                + castInstruction + "; inferredType=" + inferredType;
    }

    @Override
    public int compareTo(final DependencyFactoryEvidence other) {
        return stableKey().compareTo(other.stableKey());
    }
}
