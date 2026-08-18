package io.github.dependencyanalysis.bytecode;

import java.util.List;

/**
 * Effective ChangePoints and semantic-filter evidence for one JAR pair.
 *
 * @param changePoints effective ChangePoints
 * @param rawChangePointCount raw ChangePoint count before semantic filtering
 * @param ssaComparisons normalized SSA evidence
 * @param decompileComparisons decompiled Java comparison evidence
 */
public record BytecodeDiffResult(
        List<ChangePoint> changePoints,
        int rawChangePointCount,
        List<SsaComparisonEvidence> ssaComparisons,
        List<DecompileComparisonEvidence> decompileComparisons) {

    /** Creates an immutable result. */
    public BytecodeDiffResult {
        changePoints = List.copyOf(changePoints);
        ssaComparisons = List.copyOf(ssaComparisons);
        decompileComparisons = List.copyOf(decompileComparisons);
    }

    /** @return number of normalized SSA matches suppressed */
    public long matchedSuppressedCount() {
        return count(SsaComparisonStatus.MATCHED);
    }

    /** @return number of normalized SSA differences */
    public long differentCount() {
        return count(SsaComparisonStatus.DIFFERENT);
    }

    /** @return number of unknown normalized SSA comparisons */
    public long unknownCount() {
        return count(SsaComparisonStatus.UNKNOWN);
    }

    /** @return number of candidates whose SSA comparison was short-circuited */
    public long ssaSkippedCount() {
        return decompileComparisons.size() - ssaComparisons.size();
    }

    /** @return total normalized SSA comparison elapsed milliseconds */
    public long ssaElapsedMillis() {
        return ssaComparisons.stream()
                .mapToLong(SsaComparisonEvidence::getElapsedMillis).sum();
    }

    /** @return number of exact decompiled Java matches */
    public long javaIdenticalCount() {
        return decompileCount(DecompileComparisonStatus.IDENTICAL);
    }

    /** @return number of differing decompiled Java methods */
    public long javaDifferentCount() {
        return decompileCount(DecompileComparisonStatus.DIFFERENT);
    }

    /** @return number of unavailable decompiled Java comparisons */
    public long javaUnknownCount() {
        return decompileCount(DecompileComparisonStatus.UNKNOWN);
    }

    /** @return number of staged-filtered method body changes */
    public long semanticSuppressedCount() {
        return decompileComparisons.stream()
                .filter(DecompileComparisonEvidence::isSuppressed).count();
    }

    /** @return number of retained compared method body changes */
    public long semanticRetainedCount() {
        return decompileComparisons.size() - semanticSuppressedCount();
    }

    /** @return total decompilation comparison elapsed milliseconds */
    public long decompileElapsedMillis() {
        return decompileComparisons.stream()
                .mapToLong(DecompileComparisonEvidence::getElapsedMillis)
                .sum();
    }

    private long count(final SsaComparisonStatus status) {
        return ssaComparisons.stream()
                .filter(value -> value.getStatus() == status).count();
    }

    private long decompileCount(final DecompileComparisonStatus status) {
        return decompileComparisons.stream()
                .filter(value -> value.getStatus() == status).count();
    }
}
