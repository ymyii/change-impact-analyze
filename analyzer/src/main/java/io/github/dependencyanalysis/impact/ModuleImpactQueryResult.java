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
        paths = Collections.unmodifiableList(
                new ArrayList<>(impactPaths));
        structuralPaths = Collections.unmodifiableList(
                new ArrayList<>(structures));
        dispositions = Collections.unmodifiableMap(
                new LinkedHashMap<>(pointDispositions));
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
}
