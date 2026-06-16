package io.github.changeimpact.analyze.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of an impact
 * trace run containing sorted
 * impact paths and not-reported
 * reason statistics.
 */
public final class ImpactResult {

    /** Sorted impact paths. */
    private final List<ImpactPath>
            paths;

    /** Not-reported reason
     *  counts. */
    private final Map<NotReportedReason,
            Integer> notReported;

    /**
     * Creates a new impact result.
     *
     * @param pathList sorted paths
     * @param stats    not-reported
     *                 reason counts
     */
    public ImpactResult(
            final List<ImpactPath>
                    pathList,
            final Map<NotReportedReason,
                    Integer> stats) {
        this.paths = Collections
                .unmodifiableList(
                        new ArrayList<>(
                                pathList));
        this.notReported = Collections
                .unmodifiableMap(
                        new EnumMap<>(
                                stats));
    }

    /**
     * Returns the sorted impact
     * paths.
     *
     * @return unmodifiable path
     *  list
     */
    public List<ImpactPath>
            getPaths() {
        return paths;
    }

    /**
     * Returns the not-reported
     * reason statistics.
     *
     * @return unmodifiable map
     *  of reason to count
     */
    public Map<NotReportedReason,
            Integer>
            getNotReportedStats() {
        return notReported;
    }

    /**
     * Returns the count for a
     * specific not-reported
     * reason.
     *
     * @param reason the reason
     * @return count or 0
     */
    public int getNotReportedCount(
            final NotReportedReason
                    reason) {
        final Integer count =
                notReported.get(
                        reason);
        return count == null
                ? 0 : count;
    }

    @Override
    public String toString() {
        return "ImpactResult{"
                + "paths="
                + paths.size()
                + ", notReported="
                + notReported
                + '}';
    }
}
