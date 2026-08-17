package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeSentinelRole;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Detaches report paths and metrics from one completed WALA session. */
final class ModuleAnalysisSnapshotter {

    /**
     * Replaces all WALA-backed path nodes and clears the live session.
     *
     * @param module completed module result
     * @return report-safe module result
     */
    ModuleAnalysisResult detach(final ModuleAnalysisResult module) {
        final ModuleCallGraphSession session = module.getSession();
        if (session == null) {
            return module;
        }
        final Map<QueryNode, SnapshotQueryNode> nodes =
                new IdentityHashMap<>();
        final Map<ReferenceEvidence, ReferenceEvidence> evidence =
                new LinkedHashMap<>();
        final List<ImpactPath> paths = module.getImpactPaths().stream()
                .map(path -> impact(path, session, nodes, evidence)).toList();
        final List<StructuralReferencePath> structural = module
                .getStructuralPaths().stream()
                .map(path -> structural(path, session, nodes)).toList();
        final long contexts = session.getGraph().stream()
                .map(CGNode::getContext).distinct().count();
        final ModuleCallGraphSnapshot snapshot = new ModuleCallGraphSnapshot(
                session.getStats(), session.getEntrypointCount(),
                session.getParameterCandidateCount(),
                session.getSelectedEntrypointClassCount(), contexts,
                session.getJdkDispatchPruning().prunedTargetCount(),
                new CallGraphCoverageMapper().snapshot(
                        session.getDependencyBoundary()));
        final ChangePointEvidenceIndex frozenEvidence = freezeEvidence(
                module.getChangePointEvidence(), evidence);
        return module.toBuilder().session(null).callGraphSnapshot(snapshot)
                .impactPaths(paths)
                .structuralPaths(structural)
                .changePointEvidence(frozenEvidence).build();
    }

    private ImpactPath impact(
            final ImpactPath path,
            final ModuleCallGraphSession session,
            final Map<QueryNode, SnapshotQueryNode> snapshots,
            final Map<ReferenceEvidence, ReferenceEvidence> evidence) {
        final List<QueryNode> nodes = path.getNodes().stream()
                .map(node -> snapshot(node, session, snapshots))
                .map(QueryNode.class::cast).toList();
        final ChangePointTerminal terminal = path.getTerminal();
        return new ImpactPath(nodes, new ChangePointTerminal(
                terminal.getChangePoint(), freeze(
                terminal.getImpactEvidence(), evidence)),
                path.getClassification(), path.getRootKind());
    }

    private ChangePointEvidenceIndex freezeEvidence(
            final ChangePointEvidenceIndex index,
            final Map<ReferenceEvidence, ReferenceEvidence> evidence) {
        if (index == null) {
            return null;
        }
        final List<ChangePointEvidenceResolution> resolutions = index
                .resolutions().stream().map(resolution ->
                        new ChangePointEvidenceResolution(
                                resolution.changePoint(), resolution.status(),
                                resolution.evidence().stream()
                                        .map(value -> freeze(value, evidence))
                                        .toList(), resolution.limitations()))
                .toList();
        return new ChangePointEvidenceIndex(resolutions, Map.of(),
                index.limitations(), index.localConstantSuccessCount(),
                index.localConstantUnresolvedCount());
    }

    private ReferenceEvidence freeze(
            final ReferenceEvidence source,
            final Map<ReferenceEvidence, ReferenceEvidence> evidence) {
        return evidence.computeIfAbsent(source, value -> {
            final EvidenceAnchor anchor = value.anchor().orElse(null);
            if (!(anchor instanceof MethodEvidenceAnchor)) {
                return value;
            }
            return new ReferenceEvidence(Optional.of(
                    new StableEvidenceAnchor(anchor.stableKey())),
                    value.target(), value.kind(), value.mechanism(),
                    value.location(), value.detail());
        });
    }

    private StructuralReferencePath structural(
            final StructuralReferencePath path,
            final ModuleCallGraphSession session,
            final Map<QueryNode, SnapshotQueryNode> snapshots) {
        final List<QueryNode> nodes = path.getNodes().stream()
                .map(node -> snapshot(node, session, snapshots))
                .map(QueryNode.class::cast).toList();
        return new StructuralReferencePath(path.getChangePoint(),
                path.getReference(), nodes,
                path.getClassification());
    }

    private SnapshotQueryNode snapshot(
            final QueryNode node,
            final ModuleCallGraphSession session,
            final Map<QueryNode, SnapshotQueryNode> snapshots) {
        final SnapshotQueryNode existing = snapshots.get(node);
        if (existing != null) {
            return existing;
        }
        if (node instanceof SnapshotQueryNode value) {
            snapshots.put(node, value);
            return value;
        }
        if (!(node instanceof WalaQueryNode wala)) {
            final SnapshotQueryNode value = new SnapshotQueryNode(
                    node.methodId(), node.origin(), "synthetic evidence",
                    -1, CallGraphNodeSentinelRole.NONE);
            snapshots.put(node, value);
            return value;
        }
        final CGNode graphNode = wala.walaNode();
        final CallGraphNodeSentinelRole sentinel =
                graphNode == session.getGraph().getFakeRootNode()
                ? CallGraphNodeSentinelRole.FAKE_ROOT
                : graphNode == session.getGraph().getFakeWorldClinitNode()
                ? CallGraphNodeSentinelRole.FAKE_WORLD_CLINIT
                : CallGraphNodeSentinelRole.NONE;
        final SnapshotQueryNode value = new SnapshotQueryNode(
                node.methodId(), node.origin(),
                graphNode.getContext().toString(),
                graphNode.getGraphNodeId(), sentinel);
        snapshots.put(node, value);
        return value;
    }
}
