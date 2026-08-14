package io.github.dependencyanalysis.callgraph.topology;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

// Wiki: wiki/features/call-graph-engine.md - Benchmark topology capture
/** Produces deterministic CGNode diagnostics without changing a graph. */
public final class CallGraphTopologyAnalyzer {

    /** Ranked CGNode and child IMethod limit per direction. */
    public static final int RANK_LIMIT = 10;

    /** Stable node ordering. */
    private static final Comparator<CallGraphNodeIdentity> NODE_ORDER =
            Comparator.naturalOrder();

    /** Ranked caller/callee ordering. */
    private static final Comparator<RankValue> RANK_ORDER =
            Comparator.comparingInt(RankValue::relatedNodeCount).reversed()
                    .thenComparing(Comparator.comparingInt(
                            RankValue::distinctMethodCount).reversed())
                    .thenComparing(Comparator.comparingInt(
                            RankValue::rawEdgeCount).reversed())
                    .thenComparing(RankValue::node);

    /** Ranked child IMethod ordering. */
    private static final Comparator<RelatedValue> RELATED_ORDER =
            Comparator.comparingInt(RelatedValue::relatedNodeCount)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(
                            RelatedValue::rawEdgeCount).reversed())
                    .thenComparing(RelatedValue::method);

    /**
     * Captures CGNode-level topology from one completed graph.
     *
     * @param graph completed WALA Call Graph
     * @param origins code origin resolver
     * @return immutable topology snapshot
     */
    public CallGraphTopologySnapshot analyze(
            final CallGraph graph,
            final Function<IClass, CodeOrigin> origins) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(origins, "origins");
        final Map<CGNode, CallGraphNodeSentinelRole> sentinelRoles =
                sentinelRoles(graph);
        final Map<CGNode, CallGraphNodeIdentity> identities =
                new IdentityHashMap<>();
        final Map<CallGraphNodeIdentity, CGNode> nodes = new TreeMap<>();
        final Map<CallGraphNodeIdentity, Set<CallGraphNodeIdentity>>
                outgoing = new TreeMap<>();
        final Map<CallGraphNodeIdentity, Set<CallGraphNodeIdentity>>
                incoming = new TreeMap<>();
        final Map<CallGraphNodeIdentity, Integer> rawOutgoing =
                new HashMap<>();
        final Map<CallGraphNodeIdentity, Integer> rawIncoming =
                new HashMap<>();
        for (CGNode node : graph) {
            final CallGraphNodeIdentity identity = identity(
                    node, origins, sentinelRoles.getOrDefault(
                            node, CallGraphNodeSentinelRole.NONE));
            identities.put(node, identity);
            nodes.put(identity, node);
            outgoing.put(identity, new TreeSet<>());
            incoming.put(identity, new TreeSet<>());
        }
        int rawGraphEdges = 0;
        for (CGNode node : graph) {
            final CallGraphNodeIdentity caller = identities.get(node);
            final Iterator<CGNode> successors = graph.getSuccNodes(node);
            while (successors.hasNext()) {
                final CGNode successor = successors.next();
                rawGraphEdges++;
                final CallGraphNodeIdentity callee = identities.get(successor);
                outgoing.get(caller).add(callee);
                incoming.get(callee).add(caller);
                rawOutgoing.merge(caller, 1, Integer::sum);
                rawIncoming.merge(callee, 1, Integer::sum);
            }
        }
        final Set<CallGraphNodeIdentity> entrypoints = entrypoints(
                graph, sentinelRoles, identities);
        final Map<CallGraphNodeIdentity, CallGraphPathRootKind> roots =
                reachabilityRoots(entrypoints, sentinelRoles, identities);
        final Set<CallGraphNodeIdentity> cycleNodes = cycleNodes(
                nodes.keySet(), outgoing, incoming);
        final List<RankValue> callerRanks = ranks(
                outgoing, rawOutgoing);
        final List<RankValue> calleeRanks = ranks(
                incoming, rawIncoming);
        final Set<CallGraphNodeIdentity> targets = new LinkedHashSet<>();
        callerRanks.forEach(value -> targets.add(value.node()));
        calleeRanks.forEach(value -> targets.add(value.node()));
        final Map<CallGraphNodeIdentity,
                List<CallGraphNodeReachabilityPath>> paths = paths(
                        targets, roots, outgoing, incoming, cycleNodes);
        return new CallGraphTopologySnapshot(
                entrypoints.size(), graph.getNumberOfNodes(), rawGraphEdges,
                materialize(callerRanks, outgoing, nodes, paths, cycleNodes),
                materialize(calleeRanks, incoming, nodes, paths, cycleNodes));
    }

    private Map<CGNode, CallGraphNodeSentinelRole> sentinelRoles(
            final CallGraph graph) {
        final Map<CGNode, CallGraphNodeSentinelRole> result =
                new IdentityHashMap<>();
        if (graph.getFakeRootNode() != null) {
            result.put(graph.getFakeRootNode(),
                    CallGraphNodeSentinelRole.FAKE_ROOT);
        }
        if (graph.getFakeWorldClinitNode() != null) {
            result.put(graph.getFakeWorldClinitNode(),
                    CallGraphNodeSentinelRole.FAKE_WORLD_CLINIT);
        }
        return result;
    }

    private Set<CallGraphNodeIdentity> entrypoints(
            final CallGraph graph,
            final Map<CGNode, CallGraphNodeSentinelRole> sentinels,
            final Map<CGNode, CallGraphNodeIdentity> identities) {
        final Set<CallGraphNodeIdentity> result = new TreeSet<>();
        for (CGNode node : graph.getEntrypointNodes()) {
            if (!sentinels.containsKey(node)
                    && identities.containsKey(node)) {
                result.add(identities.get(node));
            }
        }
        return result;
    }

    private Map<CallGraphNodeIdentity, CallGraphPathRootKind>
            reachabilityRoots(
                    final Set<CallGraphNodeIdentity> entrypoints,
                    final Map<CGNode, CallGraphNodeSentinelRole> sentinels,
                    final Map<CGNode, CallGraphNodeIdentity> identities) {
        final Map<CallGraphNodeIdentity, CallGraphPathRootKind> result =
                new TreeMap<>();
        for (CallGraphNodeIdentity entrypoint : entrypoints) {
            result.put(entrypoint,
                    CallGraphPathRootKind.DECLARED_ENTRYPOINT);
        }
        for (Map.Entry<CGNode, CallGraphNodeSentinelRole> sentinel
                : sentinels.entrySet()) {
            final CallGraphNodeIdentity identity = identities.get(
                    sentinel.getKey());
            if (identity != null) {
                result.put(identity, rootKind(sentinel.getValue()));
            }
        }
        return result;
    }

    private CallGraphPathRootKind rootKind(
            final CallGraphNodeSentinelRole role) {
        return switch (role) {
            case FAKE_ROOT -> CallGraphPathRootKind.FAKE_ROOT;
            case FAKE_WORLD_CLINIT ->
                    CallGraphPathRootKind.FAKE_WORLD_CLINIT;
            case NONE -> throw new IllegalArgumentException(
                    "Ordinary CGNode is not a sentinel root");
        };
    }

    private CallGraphNodeIdentity identity(
            final CGNode node,
            final Function<IClass, CodeOrigin> origins,
            final CallGraphNodeSentinelRole sentinelRole) {
        return new CallGraphNodeIdentity(
                node.getGraphNodeId(), identity(node.getMethod(), origins),
                normalizeContext(String.valueOf(node.getContext())),
                node.getMethod().isWalaSynthetic(), sentinelRole);
    }

    private CallGraphMethodIdentity identity(
            final IMethod method,
            final Function<IClass, CodeOrigin> origins) {
        final IClass owner = method.getDeclaringClass();
        return new CallGraphMethodIdentity(
                normalizeOwner(owner.getName().toString()),
                method.getName().toString(),
                method.getDescriptor().toString(), origins.apply(owner));
    }

    private String normalizeOwner(final String owner) {
        final String withoutPrefix = owner.startsWith("L")
                ? owner.substring(1) : owner;
        return withoutPrefix.replace('/', '.');
    }

    private String normalizeContext(final String context) {
        return context.replace('\r', ' ').replace('\n', ' ')
                .replace('\t', ' ').trim();
    }

    private List<RankValue> ranks(
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> related,
            final Map<CallGraphNodeIdentity, Integer> rawEdges) {
        return related.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new RankValue(
                        entry.getKey(), entry.getValue().size(),
                        distinctMethodCount(entry.getValue()),
                        rawEdges.getOrDefault(entry.getKey(), 0)))
                .sorted(RANK_ORDER)
                .limit(RANK_LIMIT)
                .toList();
    }

    private int distinctMethodCount(
            final Set<CallGraphNodeIdentity> nodes) {
        return (int) nodes.stream().map(CallGraphNodeIdentity::method)
                .distinct().count();
    }

    private List<CallGraphRankedNode> materialize(
            final List<RankValue> ranks,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> related,
            final Map<CallGraphNodeIdentity, CGNode> nodes,
            final Map<CallGraphNodeIdentity,
                    List<CallGraphNodeReachabilityPath>> paths,
            final Set<CallGraphNodeIdentity> cycles) {
        return ranks.stream().map(value -> new CallGraphRankedNode(
                value.node(), value.relatedNodeCount(),
                value.distinctMethodCount(), value.rawEdgeCount(),
                relatedMethods(related.getOrDefault(
                        value.node(), Set.of())),
                cycles.contains(value.node()), ir(nodes.get(value.node())),
                paths.getOrDefault(value.node(), List.of()))).toList();
    }

    private List<CallGraphRelatedMethod> relatedMethods(
            final Set<CallGraphNodeIdentity> relatedNodes) {
        final Map<CallGraphMethodIdentity,
                List<CallGraphNodeIdentity>> grouped = new TreeMap<>();
        for (CallGraphNodeIdentity node : relatedNodes) {
            grouped.computeIfAbsent(node.method(), ignored ->
                    new ArrayList<>()).add(node);
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    final List<CallGraphNodeIdentity> nodes = entry.getValue()
                            .stream().sorted().toList();
                    return new RelatedValue(entry.getKey(), nodes.size(),
                            nodes.size(), nodes);
                })
                .sorted(RELATED_ORDER)
                .limit(RANK_LIMIT)
                .map(value -> new CallGraphRelatedMethod(
                        value.method(), value.rawEdgeCount(), value.nodes()))
                .toList();
    }

    private CallGraphNodeIr ir(final CGNode node) {
        if (node == null) {
            return new CallGraphNodeIr("", "CGNode was not found");
        }
        try {
            final IR value = node.getIR();
            if (value == null) {
                return new CallGraphNodeIr("", "WALA IR is unavailable");
            }
            final String text = value.toString();
            if (text == null || text.isBlank()) {
                return new CallGraphNodeIr("", "WALA IR is empty");
            }
            return new CallGraphNodeIr(text, "");
        } catch (RuntimeException exception) {
            final String message = exception.getMessage();
            return new CallGraphNodeIr("", exception.getClass().getSimpleName()
                    + (message == null || message.isBlank()
                    ? "" : ": " + message));
        }
    }

    private Map<CallGraphNodeIdentity,
            List<CallGraphNodeReachabilityPath>> paths(
            final Collection<CallGraphNodeIdentity> targets,
            final Map<CallGraphNodeIdentity, CallGraphPathRootKind> roots,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> outgoing,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> incoming,
            final Set<CallGraphNodeIdentity> cycles) {
        final Map<CallGraphNodeIdentity,
                List<CallGraphNodeReachabilityPath>> result = new TreeMap<>();
        for (CallGraphNodeIdentity target : targets) {
            final Map<CallGraphNodeIdentity, Integer> distances =
                    reverseDistances(target, incoming);
            final List<CallGraphNodeReachabilityPath> targetPaths =
                    new ArrayList<>();
            for (Map.Entry<CallGraphNodeIdentity, CallGraphPathRootKind> root
                    : roots.entrySet()) {
                if (distances.containsKey(root.getKey())) {
                    targetPaths.add(new CallGraphNodeReachabilityPath(
                            root.getValue(), root.getKey(), shortestPath(
                            root.getKey(), target,
                            distances, outgoing, cycles)));
                }
            }
            targetPaths.sort(Comparator.comparing(
                    CallGraphNodeReachabilityPath::rootKind)
                    .thenComparing(CallGraphNodeReachabilityPath::root));
            result.put(target, List.copyOf(targetPaths));
        }
        return result;
    }

    private Map<CallGraphNodeIdentity, Integer> reverseDistances(
            final CallGraphNodeIdentity target,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> incoming) {
        final Map<CallGraphNodeIdentity, Integer> distances =
                new HashMap<>();
        final Deque<CallGraphNodeIdentity> queue = new ArrayDeque<>();
        distances.put(target, 0);
        queue.add(target);
        while (!queue.isEmpty()) {
            final CallGraphNodeIdentity current = queue.removeFirst();
            final int nextDistance = distances.get(current) + 1;
            for (CallGraphNodeIdentity predecessor
                    : incoming.getOrDefault(current, Set.of())) {
                if (!distances.containsKey(predecessor)) {
                    distances.put(predecessor, nextDistance);
                    queue.addLast(predecessor);
                }
            }
        }
        return distances;
    }

    private List<CallGraphNodePathStep> shortestPath(
            final CallGraphNodeIdentity entrypoint,
            final CallGraphNodeIdentity target,
            final Map<CallGraphNodeIdentity, Integer> distances,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> outgoing,
            final Set<CallGraphNodeIdentity> cycles) {
        final List<CallGraphNodePathStep> result = new ArrayList<>();
        CallGraphNodeIdentity current = entrypoint;
        result.add(new CallGraphNodePathStep(
                current, cycles.contains(current)));
        while (!current.equals(target)) {
            final int nextDistance = distances.get(current) - 1;
            current = outgoing.getOrDefault(current, Set.of()).stream()
                    .filter(next -> distances.getOrDefault(next, -1)
                            == nextDistance)
                    .min(NODE_ORDER)
                    .orElseThrow(() -> new IllegalStateException(
                            "Broken shortest path to " + target.stableKey()));
            result.add(new CallGraphNodePathStep(
                    current, cycles.contains(current)));
        }
        return List.copyOf(result);
    }

    private Set<CallGraphNodeIdentity> cycleNodes(
            final Set<CallGraphNodeIdentity> nodes,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> outgoing,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> incoming) {
        final List<CallGraphNodeIdentity> order = finishingOrder(
                nodes, outgoing);
        final Set<CallGraphNodeIdentity> assigned = new HashSet<>();
        final Set<CallGraphNodeIdentity> cycles = new HashSet<>();
        for (int index = order.size() - 1; index >= 0; index--) {
            final CallGraphNodeIdentity root = order.get(index);
            if (!assigned.add(root)) {
                continue;
            }
            final Set<CallGraphNodeIdentity> component = new HashSet<>();
            final Deque<CallGraphNodeIdentity> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                final CallGraphNodeIdentity current = stack.pop();
                component.add(current);
                for (CallGraphNodeIdentity predecessor
                        : incoming.getOrDefault(current, Set.of())) {
                    if (assigned.add(predecessor)) {
                        stack.push(predecessor);
                    }
                }
            }
            if (component.size() > 1
                    || outgoing.getOrDefault(root, Set.of()).contains(root)) {
                cycles.addAll(component);
            }
        }
        return cycles;
    }

    private List<CallGraphNodeIdentity> finishingOrder(
            final Set<CallGraphNodeIdentity> nodes,
            final Map<CallGraphNodeIdentity,
                    Set<CallGraphNodeIdentity>> outgoing) {
        final List<CallGraphNodeIdentity> order = new ArrayList<>();
        final Set<CallGraphNodeIdentity> visited = new HashSet<>();
        for (CallGraphNodeIdentity node : nodes) {
            if (!visited.add(node)) {
                continue;
            }
            final Deque<TraversalFrame> stack = new ArrayDeque<>();
            stack.push(new TraversalFrame(node,
                    outgoing.getOrDefault(node, Set.of()).iterator()));
            while (!stack.isEmpty()) {
                final TraversalFrame frame = stack.peek();
                if (frame.successors().hasNext()) {
                    final CallGraphNodeIdentity successor =
                            frame.successors().next();
                    if (visited.add(successor)) {
                        stack.push(new TraversalFrame(successor,
                                outgoing.getOrDefault(
                                        successor, Set.of()).iterator()));
                    }
                } else {
                    order.add(frame.node());
                    stack.pop();
                }
            }
        }
        return order;
    }

    /**
     * Ranked CGNode tuple.
     *
     * @param node ranked node
     * @param relatedNodeCount related CGNode count
     * @param distinctMethodCount distinct related Method count
     * @param rawEdgeCount raw edge count
     */
    private record RankValue(
            CallGraphNodeIdentity node,
            int relatedNodeCount,
            int distinctMethodCount,
            int rawEdgeCount) {
    }

    /**
     * Ranked related IMethod tuple.
     *
     * @param method related Method
     * @param relatedNodeCount related CGNode count
     * @param rawEdgeCount raw edge count
     * @param nodes related nodes
     */
    private record RelatedValue(
            CallGraphMethodIdentity method,
            int relatedNodeCount,
            int rawEdgeCount,
            List<CallGraphNodeIdentity> nodes) {
    }

    /**
     * Iterative depth-first traversal frame.
     *
     * @param node current node
     * @param successors remaining successors
     */
    private record TraversalFrame(
            CallGraphNodeIdentity node,
            Iterator<CallGraphNodeIdentity> successors) {
    }
}
