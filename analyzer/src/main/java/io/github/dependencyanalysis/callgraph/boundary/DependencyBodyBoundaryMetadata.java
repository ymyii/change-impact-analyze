package io.github.dependencyanalysis.callgraph.boundary;

import java.util.List;
import java.util.Set;

/**
 * Immutable dependency method-body boundary output after fixed point.
 *
 * @param dangerousTransfers changed instance transfer evidence
 * @param factories flow-to-cast factory evidence
 * @param bodyBoundaryHits actually reached no-op dependency methods
 * @param noOpMethods reachable no-op method identities
 * @param realExternalMethodNodes real-IR external method nodes
 * @param noOpMethodNodes ordinary no-op method nodes
 * @param factoryMethodNodes factory summary method nodes
 * @param ancestorRetainedExternalTypeCount retained external ancestor types
 * @param ancestorRetainedExternalMethodNodeCount reachable retained methods
 * @param prunedExternalMethodTargetCount pruned external method targets
 */
public record DependencyBodyBoundaryMetadata(
        List<DependencyBoundaryTransfer> dangerousTransfers,
        List<DependencyFactoryFinding> factories,
        List<DependencyBodyBoundaryHit> bodyBoundaryHits,
        Set<String> noOpMethods,
        int realExternalMethodNodes,
        int noOpMethodNodes,
        int factoryMethodNodes,
        int ancestorRetainedExternalTypeCount,
        int ancestorRetainedExternalMethodNodeCount,
        int prunedExternalMethodTargetCount) {

    /** Snapshots deterministic boundary output. */
    public DependencyBodyBoundaryMetadata {
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
            throw new IllegalArgumentException("negative boundary metric");
        }
    }

}
