package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Typed limitation for an actually reached no-op dependency method body.
 *
 * @param calleeMethod stable callee method identity
 * @param calleeArtifact logical callee artifact
 * @param context WALA caller-independent Context identity
 */
public record DependencyBodyBoundaryHit(
        String calleeMethod,
        ArtifactCoord calleeArtifact,
        String context) implements CoverageLimitation,
        Comparable<DependencyBodyBoundaryHit> {

    /** Stable machine-readable limitation code. */
    public static final String CODE = "DEPENDENCY_BODY_BOUNDARY_REACHED";

    /** Validates stable boundary identity. */
    public DependencyBodyBoundaryHit {
        Objects.requireNonNull(calleeMethod, "calleeMethod");
        Objects.requireNonNull(calleeArtifact, "calleeArtifact");
        Objects.requireNonNull(context, "context");
    }

    @Override
    public ModuleAnalysisReason reason() {
        return ModuleAnalysisReason.INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY;
    }

    @Override
    public String stableKey() {
        return CODE + "|" + calleeArtifact + "|" + calleeMethod
                + "|" + context;
    }

    @Override
    public String summary() {
        return CODE + ": callee=" + calleeMethod + "; artifact="
                + calleeArtifact + "; context=" + context;
    }

    @Override
    public int compareTo(final DependencyBodyBoundaryHit other) {
        return stableKey().compareTo(other.stableKey());
    }
}
