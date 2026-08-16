package io.github.dependencyanalysis.bytecode;

import java.util.List;

/**
 * Effective ChangePoints and semantic-filter evidence for one JAR pair.
 *
 * @param changePoints effective ChangePoints
 * @param rawChangePointCount raw ChangePoint count before SSA filtering
 * @param ssaComparisons normalized SSA evidence
 */
public record BytecodeDiffResult(
        List<ChangePoint> changePoints,
        int rawChangePointCount,
        List<SsaComparisonEvidence> ssaComparisons) {

    /** Creates an immutable result. */
    public BytecodeDiffResult {
        changePoints = List.copyOf(changePoints);
        ssaComparisons = List.copyOf(ssaComparisons);
    }

    /** @return number of normalized SSA matches suppressed */
    public long matchedSuppressedCount() {
        return count(SsaComparisonStatus.MATCHED);
    }

    /** @return number of differing methods retained */
    public long differentRetainedCount() {
        return count(SsaComparisonStatus.DIFFERENT);
    }

    /** @return number of unknown comparisons retained */
    public long unknownRetainedCount() {
        return count(SsaComparisonStatus.UNKNOWN);
    }

    /** @return total normalized SSA comparison elapsed milliseconds */
    public long ssaElapsedMillis() {
        return ssaComparisons.stream()
                .mapToLong(SsaComparisonEvidence::getElapsedMillis).sum();
    }

    private long count(final SsaComparisonStatus status) {
        return ssaComparisons.stream()
                .filter(value -> value.getStatus() == status).count();
    }
}
