package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Full old/new decompiled text and staged suppression evidence. */
public final class DecompileComparisonEvidence {

    /** Baseline artifact. */
    private final ArtifactCoord oldArtifact;

    /** Target artifact. */
    private final ArtifactCoord newArtifact;

    /** Method body ChangePoint. */
    private final ChangePoint changePoint;

    /** Baseline class major version. */
    private final int oldMajorVersion;

    /** Target class major version. */
    private final int newMajorVersion;

    /** Baseline decompiled method. */
    private final DecompiledMethod oldMethod;

    /** Target decompiled method. */
    private final DecompiledMethod newMethod;

    /** Exact text comparison status. */
    private final DecompileComparisonStatus status;

    /** Stable comparison reason. */
    private final String reason;

    /** Decompilation and text comparison elapsed time. */
    private final long elapsedMillis;

    /** Union-filter suppression reasons. */
    private final Set<MethodBodySuppressionReason> suppressionReasons;

    /**
     * Creates Java-first method body comparison evidence.
     *
     * @param baselineArtifact baseline artifact
     * @param point compared method body change
     * @param baselineMajor baseline class major version
     * @param targetMajor target class major version
     * @param oldResult baseline decompilation
     * @param newResult target decompilation
     * @param durationMillis elapsed milliseconds
     */
    public DecompileComparisonEvidence(
            final ArtifactCoord baselineArtifact,
            final ChangePoint point,
            final int baselineMajor,
            final int targetMajor,
            final DecompiledMethod oldResult,
            final DecompiledMethod newResult,
            final long durationMillis) {
        changePoint = Objects.requireNonNull(point, "point");
        if (changePoint.getKind() != ChangePointKind.METHOD_BODY_CHANGED) {
            throw new IllegalArgumentException(
                    "Decompile evidence requires METHOD_BODY_CHANGED");
        }
        oldArtifact = Objects.requireNonNull(
                baselineArtifact, "baselineArtifact");
        newArtifact = changePoint.getArtifact();
        oldMajorVersion = baselineMajor;
        newMajorVersion = targetMajor;
        oldMethod = Objects.requireNonNull(oldResult, "oldMethod");
        newMethod = Objects.requireNonNull(newResult, "newMethod");
        elapsedMillis = durationMillis;
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis < 0");
        }
        final EnumSet<MethodBodySuppressionReason> reasons =
                EnumSet.noneOf(MethodBodySuppressionReason.class);
        if (!oldMethod.isAvailable() || !newMethod.isAvailable()) {
            status = DecompileComparisonStatus.UNKNOWN;
            reason = unavailableReason();
        } else if (oldMethod.getSource().equals(newMethod.getSource())) {
            status = DecompileComparisonStatus.IDENTICAL;
            reason = "DECOMPILED_JAVA_TEXT_IDENTICAL";
            reasons.add(MethodBodySuppressionReason.JAVA_TEXT_IDENTICAL);
        } else {
            status = DecompileComparisonStatus.DIFFERENT;
            reason = "DECOMPILED_JAVA_TEXT_DIFFERENT";
        }
        suppressionReasons = Set.copyOf(reasons);
    }

    private DecompileComparisonEvidence(
            final DecompileComparisonEvidence source,
            final Set<MethodBodySuppressionReason> reasons) {
        oldArtifact = source.oldArtifact;
        newArtifact = source.newArtifact;
        changePoint = source.changePoint;
        oldMajorVersion = source.oldMajorVersion;
        newMajorVersion = source.newMajorVersion;
        oldMethod = source.oldMethod;
        newMethod = source.newMethod;
        status = source.status;
        reason = source.reason;
        elapsedMillis = source.elapsedMillis;
        suppressionReasons = Set.copyOf(reasons);
    }

    /**
     * Adds a normalized SSA match after Java comparison did not suppress.
     *
     * @param ssa normalized SSA match evidence
     * @return evidence with the SSA suppression reason
     */
    public DecompileComparisonEvidence withSsaMatched(
            final SsaComparisonEvidence ssa) {
        final SsaComparisonEvidence matched = Objects.requireNonNull(
                ssa, "ssa");
        if (status == DecompileComparisonStatus.IDENTICAL) {
            throw new IllegalStateException(
                    "SSA must be short-circuited after identical Java text");
        }
        if (matched.getStatus() != SsaComparisonStatus.MATCHED) {
            throw new IllegalArgumentException("SSA evidence is not MATCHED");
        }
        validateIdentity(matched);
        final EnumSet<MethodBodySuppressionReason> reasons =
                EnumSet.noneOf(MethodBodySuppressionReason.class);
        reasons.addAll(suppressionReasons);
        reasons.add(MethodBodySuppressionReason.SSA_MATCHED);
        return new DecompileComparisonEvidence(this, reasons);
    }

    private void validateIdentity(final SsaComparisonEvidence ssa) {
        if (!oldArtifact.equals(ssa.getOldArtifact())
                || !newArtifact.equals(ssa.getNewArtifact())
                || !ssa.getOwner().equals(changePoint.getOwner())
                || !ssa.getName().equals(changePoint.getName())
                || !ssa.getDescriptor().equals(
                changePoint.getOldDescriptor())
                || !ssa.getOldHash().equals(changePoint.getOldHash())
                || !ssa.getNewHash().equals(changePoint.getNewHash())
                || ssa.getOldMajorVersion() != oldMajorVersion
                || ssa.getNewMajorVersion() != newMajorVersion) {
            throw new IllegalArgumentException(
                    "SSA and decompile evidence identity mismatch");
        }
    }

    private String unavailableReason() {
        if (!oldMethod.isAvailable() && !newMethod.isAvailable()) {
            return "BOTH_DECOMPILATIONS_UNAVAILABLE";
        }
        return oldMethod.isAvailable()
                ? "TARGET_DECOMPILATION_UNAVAILABLE"
                : "BASELINE_DECOMPILATION_UNAVAILABLE";
    }

    /** @return baseline artifact */
    public ArtifactCoord getOldArtifact() {
        return oldArtifact;
    }

    /** @return target artifact */
    public ArtifactCoord getNewArtifact() {
        return newArtifact;
    }

    /** @return method body ChangePoint */
    public ChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return baseline class major version */
    public int getOldMajorVersion() {
        return oldMajorVersion;
    }

    /** @return target class major version */
    public int getNewMajorVersion() {
        return newMajorVersion;
    }

    /** @return baseline decompiled method */
    public DecompiledMethod getOldMethod() {
        return oldMethod;
    }

    /** @return target decompiled method */
    public DecompiledMethod getNewMethod() {
        return newMethod;
    }

    /** @return exact text comparison status */
    public DecompileComparisonStatus getStatus() {
        return status;
    }

    /** @return stable comparison reason */
    public String getReason() {
        return reason;
    }

    /** @return elapsed milliseconds */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /** @return staged-filter suppression reasons */
    public Set<MethodBodySuppressionReason> getSuppressionReasons() {
        return suppressionReasons;
    }

    /** @return true when the first matching semantic stage suppressed */
    public boolean isSuppressed() {
        return !suppressionReasons.isEmpty();
    }

    /** @return stable logical method comparison identity */
    public String stableKey() {
        return oldArtifact + "->" + newArtifact + "|"
                + changePoint.getOwner() + "|" + changePoint.getName()
                + changePoint.getOldDescriptor();
    }

    /** @return source-free evidence for completed Module results */
    public DecompileComparisonSummary summary() {
        return new DecompileComparisonSummary(oldArtifact, newArtifact,
                changePoint.getOwner(), changePoint.getName(),
                changePoint.getOldDescriptor(), changePoint.getOldHash(),
                changePoint.getNewHash(), oldMajorVersion, newMajorVersion,
                status, reason, elapsedMillis, suppressionReasons);
    }
}
