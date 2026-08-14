package io.github.dependencyanalysis.impact;

import java.util.List;
import java.util.Set;

/**
 * Frozen Impact-layer projection of dependency body-boundary metadata.
 *
 * @param dangerousTransfers changed-instance transfers
 * @param factories flow-to-cast factory findings
 * @param bodyBoundaryHits reached no-op dependency methods
 * @param noOpMethods stable no-op method identities
 * @param realExternalMethodNodes real external method nodes
 * @param noOpMethodNodes no-op external method nodes
 * @param factoryMethodNodes factory summary method nodes
 * @param ancestorRetainedExternalTypeCount retained external ancestor types
 * @param ancestorRetainedExternalMethodNodeCount retained ancestor methods
 * @param prunedExternalMethodTargetCount pruned external targets
 */
public record DependencyBoundarySnapshot(
        List<DependencyBoundaryEvidence> dangerousTransfers,
        List<DependencyFactoryEvidence> factories,
        List<DependencyBodyBoundaryHit> bodyBoundaryHits,
        Set<String> noOpMethods,
        int realExternalMethodNodes,
        int noOpMethodNodes,
        int factoryMethodNodes,
        int ancestorRetainedExternalTypeCount,
        int ancestorRetainedExternalMethodNodeCount,
        int prunedExternalMethodTargetCount) {

    /** Validates and freezes the report projection. */
    public DependencyBoundarySnapshot {
        dangerousTransfers = dangerousTransfers.stream().distinct()
                .sorted().toList();
        factories = factories.stream().distinct().sorted().toList();
        bodyBoundaryHits = bodyBoundaryHits.stream().distinct()
                .sorted().toList();
        noOpMethods = Set.copyOf(noOpMethods);
        if (realExternalMethodNodes < 0 || noOpMethodNodes < 0
                || factoryMethodNodes < 0
                || ancestorRetainedExternalTypeCount < 0
                || ancestorRetainedExternalMethodNodeCount < 0
                || prunedExternalMethodTargetCount < 0) {
            throw new IllegalArgumentException(
                    "negative dependency boundary snapshot metric");
        }
    }

    /** @return empty frozen projection */
    public static DependencyBoundarySnapshot empty() {
        return new DependencyBoundarySnapshot(List.of(), List.of(),
                List.of(), Set.of(), 0, 0, 0, 0, 0, 0);
    }
}
