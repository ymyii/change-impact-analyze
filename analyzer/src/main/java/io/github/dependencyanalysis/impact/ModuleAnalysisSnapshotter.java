package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.callgraph.CallGraphNodeSentinelRole;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Detaches report paths and metrics from one completed WALA session. */
final class ModuleAnalysisSnapshotter {

    /**
     * Replaces all WALA-backed path nodes and clears the live session.
     *
     * @param module SSA-filtered module result
     * @return report-safe module result
     */
    ModuleAnalysisResult detach(final ModuleAnalysisResult module) {
        final ModuleCallGraphSession session = module.getSession();
        if (session == null) {
            return module;
        }
        final Map<QueryNode, SnapshotQueryNode> nodes =
                new IdentityHashMap<>();
        final List<ImpactPath> candidate = module.getCandidatePaths().stream()
                .map(path -> impact(path, session, nodes)).toList();
        final List<ImpactPath> paths = module.getFinalPaths().stream()
                .map(path -> impact(path, session, nodes)).toList();
        final List<StructuralReferencePath> structural = module
                .getStructuralPaths().stream()
                .map(path -> structural(path, session, nodes)).toList();
        final long contexts = session.getGraph().stream()
                .map(CGNode::getContext).distinct().count();
        final ModuleCallGraphSnapshot snapshot = new ModuleCallGraphSnapshot(
                session.getStats(), session.getEntrypointCount(),
                session.getParameterCandidateCount(),
                session.getSelectedEntrypointClassCount(), contexts,
                session.getDependencyBoundary());
        return module.toBuilder().session(null).callGraphSnapshot(snapshot)
                .candidatePaths(candidate).finalPaths(paths)
                .structuralPaths(structural).build();
    }

    private ImpactPath impact(
            final ImpactPath path,
            final ModuleCallGraphSession session,
            final Map<QueryNode, SnapshotQueryNode> snapshots) {
        final List<QueryNode> nodes = path.getNodes().stream()
                .map(node -> snapshot(node, session, snapshots))
                .map(QueryNode.class::cast).toList();
        return new ImpactPath(nodes, edges(path.getOrderedEdges(), nodes),
                path.getTerminal(), path.getClassification());
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
                edges(path.getOrderedEdges(), nodes),
                path.getClassification());
    }

    private List<QueryEdge> edges(
            final List<QueryEdge> source,
            final List<QueryNode> nodes) {
        final List<QueryEdge> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            final QueryEdge edge = source.get(index);
            result.add(new QueryEdge(nodes.get(index), nodes.get(index + 1),
                    edge.getKind(), edge.getEvidence(),
                    edge.getBytecodePc()));
        }
        return List.copyOf(result);
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
