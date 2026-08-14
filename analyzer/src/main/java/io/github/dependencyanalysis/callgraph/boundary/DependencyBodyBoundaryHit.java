package io.github.dependencyanalysis.callgraph.boundary;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Immutable finding for one reachable no-op dependency method body.
 *
 * @param calleeMethod stable callee identity
 * @param calleeArtifact external artifact
 * @param context callee context
 */
public record DependencyBodyBoundaryHit(
        String calleeMethod,
        ArtifactCoord calleeArtifact,
        String context) implements Comparable<DependencyBodyBoundaryHit> {

    /** Stable machine-readable finding code. */
    public static final String CODE = "DEPENDENCY_BODY_BOUNDARY_REACHED";

    /** Validates the finding. */
    public DependencyBodyBoundaryHit {
        Objects.requireNonNull(calleeMethod, "calleeMethod");
        Objects.requireNonNull(calleeArtifact, "calleeArtifact");
        Objects.requireNonNull(context, "context");
    }

    /** @return deterministic identity */
    public String stableKey() {
        return CODE + "|" + calleeArtifact + "|" + calleeMethod
                + "|" + context;
    }

    /** @return stable diagnostic summary */
    public String summary() {
        return CODE + ": callee=" + calleeMethod + "; artifact="
                + calleeArtifact + "; context=" + context;
    }

    @Override
    public int compareTo(final DependencyBodyBoundaryHit other) {
        return stableKey().compareTo(other.stableKey());
    }
}
