package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.impact.DependencyBoundaryEvidence;
import io.github.dependencyanalysis.impact.DependencyBodyBoundaryHit;
import io.github.dependencyanalysis.impact.DependencyFactoryEvidence;

import java.util.List;

/**
 * Immutable dependency method-body boundary output after fixed point.
 *
 * @param dangerousTransfers changed instance transfer evidence
 * @param factories flow-to-cast factory evidence
 * @param bodyBoundaryHits actually reached no-op dependency methods
 * @param realExternalMethodNodes real-IR external method nodes
 * @param noOpMethodNodes ordinary no-op method nodes
 * @param factoryMethodNodes factory summary method nodes
 */
public record DependencyBodyBoundaryMetadata(
        List<DependencyBoundaryEvidence> dangerousTransfers,
        List<DependencyFactoryEvidence> factories,
        List<DependencyBodyBoundaryHit> bodyBoundaryHits,
        int realExternalMethodNodes,
        int noOpMethodNodes,
        int factoryMethodNodes) {

    /** Snapshots deterministic boundary output. */
    public DependencyBodyBoundaryMetadata {
        dangerousTransfers = dangerousTransfers.stream().distinct()
                .sorted().toList();
        factories = factories.stream().distinct().sorted().toList();
        bodyBoundaryHits = bodyBoundaryHits.stream().distinct()
                .sorted().toList();
    }

    /** Compatibility constructor for propagation boundary metadata. */
    public DependencyBodyBoundaryMetadata(
            final List<DependencyBoundaryEvidence> transfers,
            final List<DependencyFactoryEvidence> factoryEvidence,
            final int realNodes,
            final int noOpNodes,
            final int factoryNodes) {
        this(transfers, factoryEvidence, List.of(), realNodes, noOpNodes,
                factoryNodes);
    }

    /** @return empty metadata for compatibility callers */
    public static DependencyBodyBoundaryMetadata empty() {
        return new DependencyBodyBoundaryMetadata(
                List.of(), List.of(), List.of(), 0, 0, 0);
    }
}
