package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.List;
import java.util.Objects;

/**
 * Typed evidence that a changed instance crosses into a no-op dependency.
 *
 * @param callerMethod caller method
 * @param callerOrigin caller origin
 * @param bytecodePc invoke bytecode program counter
 * @param invocationKind invocation kind
 * @param resolvedCallee resolved no-op callee
 * @param calleeArtifact logical callee artifact
 * @param argumentIndex minus one for receiver, otherwise zero-based argument
 * @param changedClass changed class type
 * @param typeEvidence type proof kind
 * @param dependencyPaths all paths associated with the changed class artifact
 * @param context caller Context
 */
public record DependencyBoundaryEvidence(
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
        String context) implements CoverageLimitation,
        Comparable<DependencyBoundaryEvidence> {

    /** Stable machine-readable evidence code. */
    public static final String CODE =
            "CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY";

    /** Validates and snapshots evidence. */
    public DependencyBoundaryEvidence {
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

    @Override
    public ModuleAnalysisReason reason() {
        return ModuleAnalysisReason.INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY;
    }

    @Override
    public String stableKey() {
        return CODE + "|" + callerMethod + "|" + bytecodePc + "|"
                + resolvedCallee + "|" + argumentIndex + "|"
                + changedClass + "|" + context;
    }

    @Override
    public String summary() {
        return CODE + ": caller=" + callerMethod + "; pc=" + bytecodePc
                + "; callee=" + resolvedCallee + "; argument="
                + argumentIndex + "; changedClass=" + changedClass
                + "; evidence=" + typeEvidence;
    }

    @Override
    public int compareTo(final DependencyBoundaryEvidence other) {
        return stableKey().compareTo(other.stableKey());
    }
}
