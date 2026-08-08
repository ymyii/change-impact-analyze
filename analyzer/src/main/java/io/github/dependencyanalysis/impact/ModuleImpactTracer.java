package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.DuplicateClassResolution;
import io.github.dependencyanalysis.callgraph.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.SyntheticEdgeMetadata;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/** Single-thread direct query over a live per-module WALA graph. */
public final class ModuleImpactTracer {

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates a direct module tracer.
     *
     * @param collector diagnostics
     */
    public ModuleImpactTracer(final DiagnosticLog collector) {
        diagnostics = Objects.requireNonNull(collector, "collector");
    }

    /**
     * Resolves seeds and materializes representative shortest paths.
     *
     * @param unit module analysis unit
     * @param session live WALA graph session
     * @return module query result
     */
    public ModuleImpactQueryResult trace(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session) {
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "impact-query").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        final List<ImpactPath> paths = new ArrayList<>();
        final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                new LinkedHashMap<>();
        final Map<BoundChangePoint, List<ImpactEvidence>> observations =
                new LinkedHashMap<>();
        final Set<QueryLimitation> limitations = new LinkedHashSet<>();
        final Map<QueryNode, ReverseTrace> traceCache = new HashMap<>();
        final ReachableReferenceCollector reachableReferences =
                new ReachableReferenceCollector(session);
        final ChangePointSeedResolverRegistry seedResolvers =
                new ChangePointSeedResolverRegistry();
        final List<StructuralReferenceMatch> structuralReferences =
                new StructuralReferenceResolver().resolve(
                        unit.getChangePoints(),
                        session.getStructuralReferences());
        final StructuralReferencePreparation.Result preparedStructures =
                new StructuralReferencePreparation().prepare(
                        structuralReferences, session);
        final StructuralPathResult structures = materializeStructuralPaths(
                unit.getModuleId(), preparedStructures,
                session, traceCache);
        final DynamicCallEvidenceIndex bootstrapEvidence =
                session.getDynamicEvidence();
        structures.observations().forEach((point, values) ->
                observations.put(point, values));
        limitations.addAll(structures.limitations());
        for (BoundChangePoint point : unit.getChangePoints().stream()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList()) {
            final ChangePoint change = point.getChangePoint();
            if (isAdded(change.getKind())) {
                dispositions.put(point,
                        ChangePointDisposition.CHANGE_KIND_NOT_ANALYZED);
                continue;
            }
            final ChangePointDisposition duplicateDisposition =
                    duplicateDisposition(point, session.getOwnership());
            if (duplicateDisposition != null) {
                dispositions.put(point, duplicateDisposition);
                continue;
            }
            final ChangePointSeedResolution resolution =
                    seedResolvers.resolve(new ChangePointSeedRequest(
                            unit.getModuleId(), change, session,
                            bootstrapEvidence, reachableReferences));
            final List<ImpactSeed> seeds = resolution.seeds();
            limitations.addAll(resolution.limitations());
            if (!resolution.evidence().isEmpty()) {
                final List<ImpactEvidence> merged = new ArrayList<>(
                        observations.getOrDefault(point, List.of()));
                merged.addAll(resolution.evidence());
                observations.put(point, merged.stream()
                        .distinct()
                        .sorted(Comparator.comparing(
                                ImpactEvidence::stableKey))
                        .toList());
            }
            final int before = paths.size();
            if (!seeds.isEmpty()) {
                final Map<String, ImpactPath> representative =
                        new LinkedHashMap<>();
                for (ImpactSeed seed : seeds) {
                    final ReverseTrace reverse = traceCache.computeIfAbsent(
                            seed.node(), node -> reverse(
                                    unit.getModuleId(), node, session));
                    for (ImpactPath path : materialize(
                            unit.getModuleId(), point,
                            seed, reverse, session)) {
                        final String affected = methodIdentity(
                                path.getAffectedMethod());
                        final ImpactPath previous = representative.get(
                                affected);
                        if (previous == null || representativePathComparator()
                                .compare(path, previous) < 0) {
                            representative.put(affected, path);
                        }
                    }
                }
                paths.addAll(representative.values());
            }
            dispositions.put(point, new ChangePointDispositionReducer()
                    .reduce(change.getKind(), resolution.observation(),
                            !seeds.isEmpty(), paths.size() > before,
                            structures.hasPath(point),
                            structures.unreachable().contains(point),
                            structures.observations().containsKey(point)));
        }
        paths.sort(pathComparator());
        diagnostics.info(context, "candidatePaths=" + paths.size()
                + "; structuralPaths=" + structures.paths().size()
                + "; reverseBfs=" + traceCache.size());
        diagnostics.endStage(context);
        return new ModuleImpactQueryResult(
                paths, structures.paths(), dispositions, observations,
                limitations.stream().sorted().toList());
    }

    static ChangePointDisposition duplicateDisposition(
            final BoundChangePoint point,
            final ClassOwnershipIndex ownership) {
        final DuplicateClassResolution resolution = ownership
                .duplicateResolutionOf(
                        point.getChangePoint().getOwner());
        if (resolution == null) {
            return null;
        }
        final io.github.dependencyanalysis.callgraph.ClassSource changedSource =
                io.github.dependencyanalysis.callgraph.ClassSource.artifact(
                        point.getDependencyUpgradeKey().getNewArtifact());
        return resolution.getLosers().stream()
                .anyMatch(value -> value.getSource().equals(changedSource))
                ? ChangePointDisposition.SHADOWED_BY_DUPLICATE : null;
    }

    private StructuralPathResult materializeStructuralPaths(
            final ModuleId moduleId,
            final StructuralReferencePreparation.Result prepared,
            final ModuleCallGraphSession session,
            final Map<QueryNode, ReverseTrace> traceCache) {
        final Map<String, StructuralReferencePath> selected =
                new LinkedHashMap<>();
        final Set<BoundChangePoint> unreachable = new LinkedHashSet<>();
        for (StructuralReferenceMatch match : prepared.references()) {
            final StructuralReference reference = match.reference();
            if (reference.getOrigin() == CodeOrigin.PROJECT) {
                final StructuralReferencePath path =
                        new StructuralReferencePath(match.changePoint(),
                                reference, List.of(), List.of(),
                                ImpactClassification.DIRECT);
                selected.putIfAbsent(referenceKey(match), path);
                continue;
            }
            boolean recovered = false;
            for (QueryNode seed : structuralSeeds(
                    moduleId, reference, session)) {
                final ReverseTrace reverse = traceCache.computeIfAbsent(
                        seed, node -> reverse(moduleId, node, session));
                for (StructuralReferencePath path : materializeStructural(
                        match.changePoint(), reference, reverse, session)) {
                    recovered = true;
                    final String key = referenceKey(match) + "|"
                            + methodIdentity(path.getAffectedMethod());
                    final StructuralReferencePath existing = selected.get(key);
                    if (existing == null
                            || structuralPathComparator().compare(
                            path, existing) < 0) {
                        selected.put(key, path);
                    }
                }
            }
            if (!recovered) {
                unreachable.add(match.changePoint());
            }
        }
        final List<StructuralReferencePath> paths =
                new ArrayList<>(selected.values());
        paths.sort(structuralReportComparator());
        return new StructuralPathResult(
                paths, unreachable, prepared.observations(),
                prepared.limitations());
    }

    private List<QueryNode> structuralSeeds(
            final ModuleId moduleId,
            final StructuralReference reference,
            final ModuleCallGraphSession session) {
        final Set<QueryNode> result = new LinkedHashSet<>();
        for (CGNode node : session.getGraph()) {
            if (!isSyntheticRoot(node, session)
                    && reference.getReferencingClass().equals(
                    owner(node.getMethod().getReference()))
                    && matchesStructuralMember(reference,
                    node.getMethod().getReference())) {
                result.add(queryNode(moduleId, node, session));
            }
        }
        return result.stream().sorted(queryNodeComparator()).toList();
    }

    private boolean matchesStructuralMember(
            final StructuralReference reference,
            final MethodReference method) {
        return matchesStructuralMember(reference,
                new MethodId(owner(method), method.getName().toString(),
                        method.getDescriptor().toString(), "", ""));
    }

    private boolean matchesStructuralMember(
            final StructuralReference reference,
            final MethodId method) {
        final String member = reference.getReferencingMember();
        final int descriptor = member.indexOf('(');
        if (descriptor < 0) {
            return true;
        }
        return member.startsWith(method.name() + method.descriptor());
    }

    private List<StructuralReferencePath> materializeStructural(
            final BoundChangePoint point,
            final StructuralReference reference,
            final ReverseTrace reverse,
            final ModuleCallGraphSession session) {
        final Map<String, StructuralReferencePath> result =
                new LinkedHashMap<>();
        for (QueryNode root : reverse.visited().stream()
                .filter(node -> node.origin() == CodeOrigin.PROJECT)
                .sorted(queryNodeComparator()).toList()) {
            final List<QueryNode> nodes = pathNodes(root, reverse);
            if (nodes.isEmpty()) {
                continue;
            }
            final List<QueryEdge> edges = pathEdges(nodes, session);
            result.putIfAbsent(methodIdentity(root.methodId()),
                    new StructuralReferencePath(point, reference,
                            nodes, edges, ImpactClassification.TRANSITIVE));
        }
        return List.copyOf(result.values());
    }

    private List<QueryNode> pathNodes(
            final QueryNode root, final ReverseTrace reverse) {
        final List<QueryNode> nodes = new ArrayList<>();
        QueryNode current = root;
        nodes.add(current);
        while (!current.equals(reverse.seed())) {
            current = reverse.next().get(current);
            if (current == null) {
                return List.of();
            }
            nodes.add(current);
        }
        return nodes;
    }

    private List<QueryEdge> pathEdges(
            final List<QueryNode> nodes,
            final ModuleCallGraphSession session) {
        final List<QueryEdge> edges = new ArrayList<>();
        for (int index = 0; index + 1 < nodes.size(); index++) {
            edges.add(queryEdge(nodes.get(index), nodes.get(index + 1),
                    session));
        }
        return edges;
    }

    private String referenceKey(final StructuralReferenceMatch match) {
        return match.changePoint().stableKey() + "|"
                + match.reference().stableKey();
    }

    private ReverseTrace reverse(
            final ModuleId moduleId,
            final QueryNode seed,
            final ModuleCallGraphSession session) {
        final Map<QueryNode, QueryNode> next = new HashMap<>();
        final Queue<QueryNode> queue = new ArrayDeque<>();
        final Set<QueryNode> visited = new HashSet<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            final QueryNode current = queue.remove();
            final List<QueryNode> predecessors = predecessors(
                    moduleId, current, session);
            predecessors.sort(queryNodeComparator());
            for (QueryNode predecessor : predecessors) {
                if (visited.add(predecessor)) {
                    next.put(predecessor, current);
                    queue.add(predecessor);
                }
            }
        }
        return new ReverseTrace(seed, next, visited);
    }

    private List<ImpactPath> materialize(
            final ModuleId moduleId,
            final BoundChangePoint point,
            final ImpactSeed seed,
            final ReverseTrace reverse,
            final ModuleCallGraphSession session) {
        final Map<String, ImpactPath> byAffectedMethod =
                new LinkedHashMap<>();
        final List<QueryNode> candidates = reverse.visited().stream()
                .filter(node -> node.origin() == CodeOrigin.PROJECT)
                .sorted(queryNodeComparator())
                .toList();
        for (QueryNode root : candidates) {
            final List<QueryNode> nodes = new ArrayList<>();
            QueryNode current = root;
            nodes.add(current);
            while (!current.equals(reverse.seed())) {
                current = reverse.next().get(current);
                if (current == null) {
                    break;
                }
                nodes.add(current);
            }
            if (!nodes.get(nodes.size() - 1)
                    .equals(reverse.seed())) {
                continue;
            }
            final List<QueryEdge> edges = new ArrayList<>();
            for (int index = 0; index + 1 < nodes.size(); index++) {
                edges.add(queryEdge(nodes.get(index), nodes.get(index + 1),
                        session));
            }
            final ImpactClassification classification =
                    seed.node().origin() == CodeOrigin.PROJECT
                            ? ImpactClassification.DIRECT
                            : ImpactClassification.TRANSITIVE;
            final ImpactPath path = new ImpactPath(nodes, edges,
                    new ChangePointTerminal(point, seed.kind(),
                            seed.evidence()), classification);
            final String key = methodIdentity(
                    root.methodId());
            byAffectedMethod.putIfAbsent(key, path);
        }
        return List.copyOf(byAffectedMethod.values());
    }

    private QueryEdge queryEdge(
            final QueryNode caller,
            final QueryNode callee,
            final ModuleCallGraphSession session) {
        if (!(caller instanceof WalaQueryNode)
                || !(callee instanceof WalaQueryNode)) {
            throw new IllegalStateException(
                    "Impact paths must contain WALA graph nodes");
        }
        final CGNode callerNode = ((WalaQueryNode) caller).walaNode();
        final CGNode calleeNode = ((WalaQueryNode) callee).walaNode();
        final List<CallSiteReference> sites = iteratorList(
                session.getGraph().getPossibleSites(
                        callerNode, calleeNode));
        sites.sort(Comparator
                .comparingInt(CallSiteReference::getProgramCounter)
                .thenComparing(site ->
                        site.getDeclaredTarget().toString()));
        if (sites.isEmpty()) {
            return new QueryEdge(caller, callee,
                    EdgeKind.INVOKE_SPECIAL, "WALA_IMPLICIT",
                    QueryEdge.UNKNOWN_PC);
        }
        final CallSiteReference site = sites.get(0);
        final SyntheticEdgeMetadata synthetic =
                session.syntheticEdge(callerNode);
        if (synthetic != null) {
            return new QueryEdge(caller, callee, synthetic.kind(),
                    synthetic.evidence(), site.getProgramCounter());
        }
        return new QueryEdge(caller, callee,
                invocationKind(site.getInvocationCode()),
                "declaredTarget=" + site.getDeclaredTarget(),
                site.getProgramCounter());
    }

    private WalaQueryNode queryNode(
            final ModuleId moduleId,
            final CGNode node,
            final ModuleCallGraphSession session) {
        final IMethod method = node.getMethod();
        final String owner = owner(method.getReference());
        final ClassOwnership ownership =
                session.getOwnership().ownershipOf(owner);
        final CodeOrigin codeOrigin = origin(node, session);
        final String module = codeOrigin == CodeOrigin.PROJECT
                ? moduleId.stableKey() : codeOrigin.name();
        final String source = ownership == null
                ? codeOrigin == CodeOrigin.JDK
                ? "<jdk>" : "<synthetic>"
                : ownership.getSource().toString();
        return new WalaQueryNode(node, new MethodId(owner,
                method.getName().toString(),
                method.getDescriptor().toString(), module, source),
                codeOrigin);
    }


    private CodeOrigin origin(
            final CGNode node,
            final ModuleCallGraphSession session) {
        return session.originOf(
                node.getMethod().getDeclaringClass());
    }

    private boolean isSyntheticRoot(
            final CGNode node,
            final ModuleCallGraphSession session) {
        return node.equals(session.getGraph().getFakeRootNode())
                || node.equals(session.getGraph().getFakeWorldClinitNode());
    }

    private boolean isAdded(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_ADDED
                || kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.FIELD_ADDED;
    }

    private Comparator<QueryNode> queryNodeComparator() {
        return Comparator.comparing((QueryNode node) ->
                        methodIdentity(node.methodId()))
                .thenComparingInt(this::queryNodeNumber);
    }

    private Comparator<ImpactPath> pathComparator() {
        return Comparator
                .comparing((ImpactPath path) ->
                        path.getAffectedMethod().owner())
                .thenComparing(path -> path.getAffectedMethod().name())
                .thenComparing(path ->
                        path.getAffectedMethod().descriptor())
                .thenComparing(path -> path.getTerminal()
                        .getChangePoint().stableKey());
    }

    private Comparator<ImpactPath> representativePathComparator() {
        return Comparator
                .comparingInt((ImpactPath path) ->
                        path.getOrderedEdges().size())
                .thenComparing(this::impactPathStableKey);
    }

    private String impactPathStableKey(final ImpactPath path) {
        final StringBuilder result = new StringBuilder();
        for (QueryNode node : path.getNodes()) {
            result.append(methodIdentity(node.methodId())).append('|')
                    .append(queryNodeNumber(node)).append('|');
        }
        for (QueryEdge edge : path.getOrderedEdges()) {
            result.append(edge.getBytecodePc()).append(':')
                    .append(edge.getKind()).append(':')
                    .append(edge.getEvidence()).append('|');
        }
        return result.toString();
    }

    private Comparator<StructuralReferencePath> structuralPathComparator() {
        return Comparator
                .comparingInt((StructuralReferencePath path) ->
                        path.getOrderedEdges().size())
                .thenComparing(this::structuralPathStableKey);
    }

    private Comparator<StructuralReferencePath> structuralReportComparator() {
        return Comparator
                .comparing((StructuralReferencePath path) ->
                        path.getChangePoint().stableKey())
                .thenComparing(path -> path.getReference().stableKey())
                .thenComparing(this::affectedMethodKey)
                .thenComparing(this::structuralPathStableKey);
    }

    private String affectedMethodKey(final StructuralReferencePath path) {
        return path.getAffectedMethod() == null ? ""
                : methodIdentity(path.getAffectedMethod());
    }

    private String structuralPathStableKey(
            final StructuralReferencePath path) {
        final StringBuilder result = new StringBuilder();
        for (QueryNode node : path.getNodes()) {
            result.append(methodIdentity(node.methodId())).append('|');
        }
        for (QueryEdge edge : path.getOrderedEdges()) {
            result.append(edge.getBytecodePc()).append(':')
                    .append(edge.getKind()).append(':')
                    .append(edge.getEvidence()).append('|');
        }
        return result.toString();
    }

    private EdgeKind invocationKind(
            final IInvokeInstruction.IDispatch dispatch) {
        if (dispatch == IInvokeInstruction.Dispatch.STATIC) {
            return EdgeKind.INVOKE_STATIC;
        }
        if (dispatch == IInvokeInstruction.Dispatch.SPECIAL) {
            return EdgeKind.INVOKE_SPECIAL;
        }
        if (dispatch == IInvokeInstruction.Dispatch.INTERFACE) {
            return EdgeKind.INVOKE_INTERFACE;
        }
        return EdgeKind.INVOKE_VIRTUAL;
    }

    private String methodIdentity(final MethodReference method) {
        return owner(method) + "#" + method.getName() + "#"
                + method.getDescriptor();
    }

    private String methodIdentity(final MethodId method) {
        return method.owner() + "#" + method.name() + "#"
                + method.descriptor();
    }

    private int queryNodeNumber(final QueryNode node) {
        return node instanceof WalaQueryNode
                ? ((WalaQueryNode) node).walaNode().getGraphNodeId()
                : Integer.MAX_VALUE;
    }

    private List<QueryNode> predecessors(
            final ModuleId moduleId,
            final QueryNode node,
            final ModuleCallGraphSession session) {
        final Set<QueryNode> result = new LinkedHashSet<>();
        if (node instanceof WalaQueryNode) {
            final CGNode walaNode = ((WalaQueryNode) node).walaNode();
            final List<CGNode> walaPredecessors = iteratorList(
                    session.getGraph().getPredNodes(walaNode));
            walaPredecessors.removeIf(value ->
                    isSyntheticRoot(value, session));
            for (CGNode predecessor : walaPredecessors) {
                result.add(queryNode(
                        moduleId, predecessor, session));
            }
        }
        return new ArrayList<>(result);
    }

    private String owner(final MethodReference method) {
        return owner(method.getDeclaringClass());
    }

    private String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private <T> List<T> iteratorList(final Iterator<T> iterator) {
        final List<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }


    /**
     * Per-seed backward slice, never a whole-graph predecessor copy.
     *
     * @param seed exact seed node
     * @param next shortest-path successor map
     * @param visited reachable predecessor nodes
     */
    private record ReverseTrace(
            QueryNode seed,
            Map<QueryNode, QueryNode> next,
            Set<QueryNode> visited) {
    }

    /**
     * Materialized structural paths and unreachable metadata evidence.
     *
     * @param paths reportable structural paths
     * @param unreachable references without a PROJECT boundary
     * @param observations typed access observations
     * @param limitations structural access resolution failures
     */
    private record StructuralPathResult(
            List<StructuralReferencePath> paths,
            Set<BoundChangePoint> unreachable,
            Map<BoundChangePoint, List<ImpactEvidence>> observations,
            List<QueryLimitation> limitations) {

        boolean hasPath(final BoundChangePoint point) {
            return paths.stream().anyMatch(path ->
                    path.getChangePoint().equals(point));
        }
    }

}
