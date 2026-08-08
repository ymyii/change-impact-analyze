package io.github.dependencyanalysis.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic direct-WALA query output for one module. */
public final class ModuleImpactQueryResult {

    /** Candidate Impact Paths. */
    private final List<ImpactPath> paths;

    /** Structural Reference Paths. */
    private final List<StructuralReferencePath> structuralPaths;

    /** One final disposition per bound ChangePoint. */
    private final Map<BoundChangePoint, ChangePointDisposition> dispositions;

    /** Typed observations retained even when no Impact Path is created. */
    private final Map<BoundChangePoint, List<ImpactEvidence>> observations;

    /** Typed query coverage limitations. */
    private final List<QueryLimitation> limitations;

    /**
     * Creates a module query result.
     *
     * @param impactPaths candidate paths
     * @param structures structural impacts
     * @param pointDispositions point dispositions
     */
    ModuleImpactQueryResult(
            final List<ImpactPath> impactPaths,
            final List<StructuralReferencePath> structures,
            final Map<BoundChangePoint,
                    ChangePointDisposition> pointDispositions) {
        this(impactPaths, structures, pointDispositions, Map.of(), List.of());
    }

    /**
     * Creates a module query result with typed reference observations.
     *
     * @param impactPaths candidate paths
     * @param structures structural impacts
     * @param pointDispositions point dispositions
     * @param pointObservations typed evidence by ChangePoint
     */
    ModuleImpactQueryResult(
            final List<ImpactPath> impactPaths,
            final List<StructuralReferencePath> structures,
            final Map<BoundChangePoint,
                    ChangePointDisposition> pointDispositions,
            final Map<BoundChangePoint,
                    List<ImpactEvidence>> pointObservations) {
        this(impactPaths, structures, pointDispositions, pointObservations,
                List.of());
    }

    /**
     * Creates a complete typed query result.
     *
     * @param impactPaths candidate paths
     * @param structures structural impacts
     * @param pointDispositions point dispositions
     * @param pointObservations typed evidence by ChangePoint
     * @param queryLimitations query coverage limitations
     */
    ModuleImpactQueryResult(
            final List<ImpactPath> impactPaths,
            final List<StructuralReferencePath> structures,
            final Map<BoundChangePoint,
                    ChangePointDisposition> pointDispositions,
            final Map<BoundChangePoint,
                    List<ImpactEvidence>> pointObservations,
            final List<QueryLimitation> queryLimitations) {
        paths = Collections.unmodifiableList(
                new ArrayList<>(impactPaths));
        structuralPaths = Collections.unmodifiableList(
                new ArrayList<>(structures));
        dispositions = Collections.unmodifiableMap(
                new LinkedHashMap<>(pointDispositions));
        final Map<BoundChangePoint, List<ImpactEvidence>> evidence =
                new LinkedHashMap<>();
        pointObservations.forEach((point, values) ->
                evidence.put(point, List.copyOf(values)));
        observations = Collections.unmodifiableMap(evidence);
        limitations = queryLimitations.stream().distinct().sorted().toList();
    }

    /** @return candidate Impact Paths */
    public List<ImpactPath> getPaths() {
        return paths;
    }

    /** @return Structural Reference Paths */
    public List<StructuralReferencePath> getStructuralPaths() {
        return structuralPaths;
    }

    /** @return point dispositions */
    public Map<BoundChangePoint, ChangePointDisposition> getDispositions() {
        return dispositions;
    }

    /** @return typed reference observations by ChangePoint */
    public Map<BoundChangePoint, List<ImpactEvidence>> getObservations() {
        return observations;
    }

    /** @return typed query coverage limitations */
    public List<QueryLimitation> getLimitations() {
        return limitations;
    }
}
