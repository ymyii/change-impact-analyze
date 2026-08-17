package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic direct-WALA query output for one module. */
public final class ModuleImpactQueryResult {

    /** Reported Impact Paths. */
    private final List<ImpactPath> paths;

    /** Structural Reference Paths. */
    private final List<StructuralReferencePath> structuralPaths;

    /** One final disposition per bound ChangePoint. */
    private final Map<BoundChangePoint, ChangePointDisposition> dispositions;

    /** Typed observations retained even when no Impact Path is created. */
    private final Map<BoundChangePoint, List<ImpactEvidence>> observations;

    /** Typed query coverage limitations. */
    private final List<QueryLimitation> limitations;

    /** Query-time fixed Impact Path pruning summary. */
    private final ImpactPathPruningSummary impactPathPruning;

    /**
     * Creates a module query result.
     *
     * @param impactPaths reported impact paths
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
     * @param impactPaths reported impact paths
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
     * @param impactPaths reported impact paths
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
        this(impactPaths, structures, pointDispositions, pointObservations,
                queryLimitations,
                ImpactPathPruningSummary.notExecuted());
    }

    /**
     * Creates a complete typed query result with pruning evidence.
     *
     * @param impactPaths reported impact paths
     * @param structures structural impacts
     * @param pointDispositions point dispositions
     * @param pointObservations typed evidence by ChangePoint
     * @param queryLimitations query coverage limitations
     * @param pruning query-time fixed Impact Path pruning summary
     */
    ModuleImpactQueryResult(
            final List<ImpactPath> impactPaths,
            final List<StructuralReferencePath> structures,
            final Map<BoundChangePoint,
                    ChangePointDisposition> pointDispositions,
            final Map<BoundChangePoint,
                    List<ImpactEvidence>> pointObservations,
            final List<QueryLimitation> queryLimitations,
            final ImpactPathPruningSummary pruning) {
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
        impactPathPruning = java.util.Objects.requireNonNull(
                pruning, "pruning");
    }

    /** @return reported Impact Paths */
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

    /** @return query-time fixed Impact Path pruning summary */
    public ImpactPathPruningSummary getImpactPathPruning() {
        return impactPathPruning;
    }
}
