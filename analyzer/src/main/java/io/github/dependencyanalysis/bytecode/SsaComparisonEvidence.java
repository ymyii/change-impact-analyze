package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyChange;

import java.util.Objects;

/** Auditable normalized SSA evidence produced during ChangePoint collection. */
public final class SsaComparisonEvidence {

    /** Baseline artifact. */
    private final ArtifactCoord oldArtifact;

    /** Target artifact. */
    private final ArtifactCoord newArtifact;

    /** Internal class owner. */
    private final String owner;

    /** Method name. */
    private final String name;

    /** JVM method descriptor. */
    private final String descriptor;

    /** Baseline body hash. */
    private final String oldHash;

    /** Target body hash. */
    private final String newHash;

    /** Baseline class file major version. */
    private final int oldMajorVersion;

    /** Target class file major version. */
    private final int newMajorVersion;

    /** Comparison outcome. */
    private final SsaComparisonStatus status;

    /** Stable outcome reason. */
    private final String reason;

    /** Comparison elapsed time. */
    private final long elapsedMillis;

    /**
     * Creates immutable comparison evidence.
     *
     * @param change logical dependency change
     * @param methodBodyChange method body ChangePoint
     * @param baselineMajor baseline class major version
     * @param targetMajor target class major version
     * @param outcome comparison outcome
     * @param outcomeReason stable outcome reason
     * @param durationMillis comparison elapsed time
     */
    public SsaComparisonEvidence(
            final DependencyChange change,
            final ChangePoint methodBodyChange,
            final int baselineMajor,
            final int targetMajor,
            final SsaComparisonStatus outcome,
            final String outcomeReason,
            final long durationMillis) {
        final DependencyChange dependency = Objects.requireNonNull(
                change, "change");
        final ChangePoint point = Objects.requireNonNull(
                methodBodyChange, "methodBodyChange");
        if (point.getKind() != ChangePointKind.METHOD_BODY_CHANGED) {
            throw new IllegalArgumentException(
                    "SSA evidence requires METHOD_BODY_CHANGED");
        }
        oldArtifact = dependency.getOldArtifact();
        newArtifact = dependency.getNewArtifact();
        owner = point.getOwner();
        name = point.getName();
        descriptor = point.getOldDescriptor();
        oldHash = point.getOldHash();
        newHash = point.getNewHash();
        oldMajorVersion = baselineMajor;
        newMajorVersion = targetMajor;
        status = Objects.requireNonNull(outcome, "status");
        reason = Objects.requireNonNull(outcomeReason, "reason");
        elapsedMillis = durationMillis;
    }

    /** @return baseline artifact */
    public ArtifactCoord getOldArtifact() {
        return oldArtifact;
    }

    /** @return target artifact */
    public ArtifactCoord getNewArtifact() {
        return newArtifact;
    }

    /** @return internal class owner */
    public String getOwner() {
        return owner;
    }

    /** @return method name */
    public String getName() {
        return name;
    }

    /** @return JVM method descriptor */
    public String getDescriptor() {
        return descriptor;
    }

    /** @return baseline bytecode body hash */
    public String getOldHash() {
        return oldHash;
    }

    /** @return target bytecode body hash */
    public String getNewHash() {
        return newHash;
    }

    /** @return baseline class file major version */
    public int getOldMajorVersion() {
        return oldMajorVersion;
    }

    /** @return target class file major version */
    public int getNewMajorVersion() {
        return newMajorVersion;
    }

    /** @return normalized SSA comparison status */
    public SsaComparisonStatus getStatus() {
        return status;
    }

    /** @return stable comparison reason */
    public String getReason() {
        return reason;
    }

    /** @return comparison elapsed milliseconds */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /** @return stable logical comparison identity */
    public String stableKey() {
        return oldArtifact + "->" + newArtifact + "|" + owner + "|"
                + name + descriptor;
    }

    @Override
    public boolean equals(final Object value) {
        if (!(value instanceof SsaComparisonEvidence other)) {
            return false;
        }
        return oldArtifact.equals(other.oldArtifact)
                && newArtifact.equals(other.newArtifact)
                && owner.equals(other.owner)
                && name.equals(other.name)
                && descriptor.equals(other.descriptor)
                && oldHash.equals(other.oldHash)
                && newHash.equals(other.newHash)
                && oldMajorVersion == other.oldMajorVersion
                && newMajorVersion == other.newMajorVersion
                && status == other.status
                && reason.equals(other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldArtifact, newArtifact, owner, name,
                descriptor, oldHash, newHash, oldMajorVersion,
                newMajorVersion, status, reason);
    }
}
