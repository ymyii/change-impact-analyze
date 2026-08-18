package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Set;

/**
 * Source-free decompiled Java comparison evidence safe for Module results.
 *
 * @param oldArtifact baseline artifact
 * @param newArtifact target artifact
 * @param owner internal class owner
 * @param name method name
 * @param descriptor JVM method descriptor
 * @param oldHash baseline method body hash
 * @param newHash target method body hash
 * @param oldMajorVersion baseline class major version
 * @param newMajorVersion target class major version
 * @param status decompiled Java comparison status
 * @param reason stable comparison reason
 * @param elapsedMillis comparison elapsed milliseconds
 * @param suppressionReasons staged-filter suppression reasons
 */
public record DecompileComparisonSummary(
        ArtifactCoord oldArtifact,
        ArtifactCoord newArtifact,
        String owner,
        String name,
        String descriptor,
        String oldHash,
        String newHash,
        int oldMajorVersion,
        int newMajorVersion,
        DecompileComparisonStatus status,
        String reason,
        long elapsedMillis,
        Set<MethodBodySuppressionReason> suppressionReasons) {

    /** Creates an immutable summary. */
    public DecompileComparisonSummary {
        java.util.Objects.requireNonNull(oldArtifact, "oldArtifact");
        java.util.Objects.requireNonNull(newArtifact, "newArtifact");
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(descriptor, "descriptor");
        java.util.Objects.requireNonNull(oldHash, "oldHash");
        java.util.Objects.requireNonNull(newHash, "newHash");
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(reason, "reason");
        suppressionReasons = Set.copyOf(suppressionReasons);
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis < 0");
        }
    }

    /** @return stable logical method comparison identity */
    public String stableKey() {
        return oldArtifact + "->" + newArtifact + "|" + owner + "|"
                + name + descriptor;
    }
}
