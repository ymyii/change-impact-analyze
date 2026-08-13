package io.github.dependencyanalysis.impact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable complete BoundChangePoint-to-evidence index. */
public final class ChangePointEvidenceIndex {

    /** Resolution by exact bound change. */
    private final Map<BoundChangePoint, ChangePointEvidenceResolution>
            resolutions;

    /** Module-wide limitations not attributable to one change. */
    private final List<CoverageLimitation> limitations;

    /** Successful caller-local constant resolutions. */
    private final int localConstantSuccessCount;

    /** Unresolved caller-local constant resolutions. */
    private final int localConstantUnresolvedCount;

    /**
     * Creates a stable complete index.
     *
     * @param values complete per-change resolutions
     * @param moduleLimitations module-wide limitations
     */
    public ChangePointEvidenceIndex(
            final List<ChangePointEvidenceResolution> values,
            final List<? extends CoverageLimitation> moduleLimitations) {
        this(values, moduleLimitations, 0, 0);
    }

    /**
     * Creates a stable complete index with local-resolution metrics.
     *
     * @param values complete per-change resolutions
     * @param moduleLimitations module-wide limitations
     * @param localSuccess exact resolved protocol argument count
     * @param localUnresolved exact unresolved protocol argument count
     */
    public ChangePointEvidenceIndex(
            final List<ChangePointEvidenceResolution> values,
            final List<? extends CoverageLimitation> moduleLimitations,
            final int localSuccess,
            final int localUnresolved) {
        final Map<BoundChangePoint, ChangePointEvidenceResolution> indexed =
                new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").stream()
                .sorted(java.util.Comparator.comparing(value ->
                        value.changePoint().stableKey()))
                .forEach(value -> {
                    if (indexed.put(value.changePoint(), value) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate ChangePoint evidence resolution: "
                                        + value.changePoint().stableKey());
                    }
                });
        resolutions = Map.copyOf(indexed);
        limitations = new java.util.ArrayList<CoverageLimitation>(
                Objects.requireNonNull(moduleLimitations,
                        "moduleLimitations")).stream()
                .distinct().sorted(java.util.Comparator.comparing(
                        CoverageLimitation::stableKey)).toList();
        if (localSuccess < 0 || localUnresolved < 0) {
            throw new IllegalArgumentException(
                    "Local constant resolution counts must be non-negative");
        }
        localConstantSuccessCount = localSuccess;
        localConstantUnresolvedCount = localUnresolved;
    }

    /**
     * @param point exact bound change
     * @return exact resolution, failing when the index is incomplete
     */
    public ChangePointEvidenceResolution resolution(
            final BoundChangePoint point) {
        final ChangePointEvidenceResolution value = resolutions.get(
                Objects.requireNonNull(point, "point"));
        if (value == null) {
            throw new IllegalStateException(
                    "Missing ChangePoint evidence resolution: "
                            + point.stableKey());
        }
        return value;
    }

    /** @return all stable resolutions */
    public List<ChangePointEvidenceResolution> resolutions() {
        return List.copyOf(resolutions.values());
    }

    /** @return module-wide typed limitations */
    public List<CoverageLimitation> limitations() {
        return limitations;
    }

    /** @return exact resolved caller-local protocol argument count */
    public int localConstantSuccessCount() {
        return localConstantSuccessCount;
    }

    /** @return exact unresolved caller-local protocol argument count */
    public int localConstantUnresolvedCount() {
        return localConstantUnresolvedCount;
    }
}
