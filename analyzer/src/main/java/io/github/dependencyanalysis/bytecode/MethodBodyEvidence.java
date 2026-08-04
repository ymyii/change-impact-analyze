package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Immutable old/new decompiled evidence
 * for one changed method body.
 */
public final class MethodBodyEvidence {

    /** Bytecode change point. */
    private final ChangePoint changePoint;

    /** Baseline artifact. */
    private final ArtifactCoord oldArtifact;

    /** Target artifact. */
    private final ArtifactCoord newArtifact;

    /** Baseline decompilation. */
    private final DecompiledMethod oldMethod;

    /** Target decompilation. */
    private final DecompiledMethod newMethod;

    /**
     * Creates method body evidence.
     *
     * @param point change point
     * @param oldArt baseline artifact
     * @param newArt target artifact
     * @param oldResult baseline result
     * @param newResult target result
     */
    public MethodBodyEvidence(
            final ChangePoint point,
            final ArtifactCoord oldArt,
            final ArtifactCoord newArt,
            final DecompiledMethod oldResult,
            final DecompiledMethod newResult) {
        this.changePoint = Objects.requireNonNull(
                point, "changePoint");
        if (point.getKind()
                != ChangePointKind.METHOD_BODY_CHANGED) {
            throw new IllegalArgumentException(
                    "Method body evidence requires "
                            + "METHOD_BODY_CHANGED");
        }
        this.oldArtifact = Objects.requireNonNull(
                oldArt, "oldArtifact");
        this.newArtifact = Objects.requireNonNull(
                newArt, "newArtifact");
        this.oldMethod = Objects.requireNonNull(
                oldResult, "oldMethod");
        this.newMethod = Objects.requireNonNull(
                newResult, "newMethod");
    }

    /** @return changed method */
    public ChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return baseline artifact */
    public ArtifactCoord getOldArtifact() {
        return oldArtifact;
    }

    /** @return target artifact */
    public ArtifactCoord getNewArtifact() {
        return newArtifact;
    }

    /** @return baseline decompilation */
    public DecompiledMethod getOldMethod() {
        return oldMethod;
    }

    /** @return target decompilation */
    public DecompiledMethod getNewMethod() {
        return newMethod;
    }
}
