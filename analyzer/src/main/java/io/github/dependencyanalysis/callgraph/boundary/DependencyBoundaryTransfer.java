package io.github.dependencyanalysis.callgraph.boundary;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.List;
import java.util.Objects;

/**
 * Immutable changed-instance transfer finding at a no-op body boundary.
 *
 * @param callerMethod caller method identity
 * @param callerOrigin caller code origin
 * @param bytecodePc caller bytecode program counter
 * @param invocationKind invocation kind
 * @param resolvedCallee resolved external callee
 * @param calleeArtifact external artifact
 * @param argumentIndex receiver or argument index
 * @param changedClass transferred changed class
 * @param typeEvidence local type evidence
 * @param dependencyPaths changed dependency paths
 * @param context caller context
 */
public record DependencyBoundaryTransfer(
        String callerMethod,
        CodeOrigin callerOrigin,
        int bytecodePc,
        String invocationKind,
        String resolvedCallee,
        ArtifactCoord calleeArtifact,
        int argumentIndex,
        String changedClass,
        String typeEvidence,
        List<String> dependencyPaths,
        String context) implements Comparable<DependencyBoundaryTransfer> {

    /** Stable machine-readable finding code. */
    public static final String CODE = "CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY";

    /** Validates and freezes the finding. */
    public DependencyBoundaryTransfer {
        Objects.requireNonNull(callerMethod, "callerMethod");
        Objects.requireNonNull(callerOrigin, "callerOrigin");
        Objects.requireNonNull(invocationKind, "invocationKind");
        Objects.requireNonNull(resolvedCallee, "resolvedCallee");
        Objects.requireNonNull(calleeArtifact, "calleeArtifact");
        Objects.requireNonNull(changedClass, "changedClass");
        Objects.requireNonNull(typeEvidence, "typeEvidence");
        dependencyPaths = List.copyOf(Objects.requireNonNull(
                dependencyPaths, "dependencyPaths"));
        Objects.requireNonNull(context, "context");
    }

    /** @return deterministic identity */
    public String stableKey() {
        return CODE + "|" + callerMethod + "|" + bytecodePc + "|"
                + resolvedCallee + "|" + argumentIndex + "|"
                + changedClass + "|" + context;
    }

    /** @return stable diagnostic summary */
    public String summary() {
        return CODE + ": caller=" + callerMethod + "; pc=" + bytecodePc
                + "; callee=" + resolvedCallee + "; argument="
                + argumentIndex + "; changedClass=" + changedClass
                + "; evidence=" + typeEvidence;
    }

    @Override
    public int compareTo(final DependencyBoundaryTransfer other) {
        return stableKey().compareTo(other.stableKey());
    }
}
