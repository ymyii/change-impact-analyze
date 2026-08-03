package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
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
    private final DiagnosticCollector diagnostics;

    /**
     * Creates a direct module tracer.
     *
     * @param collector diagnostics
     */
    public ModuleImpactTracer(final DiagnosticCollector collector) {
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
        final DiagnosticContext context = DiagnosticContext.task(
                "module-analysis", "impact-query").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        final List<ImpactPath> paths = new ArrayList<>();
        final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                new LinkedHashMap<>();
        final Map<QueryNode, ReverseTrace> traceCache = new HashMap<>();
        final StructuralScanResult structures =
                new StructuralImpactScanner().scan(unit, session);
        final InvokeDynamicEvidenceIndex bootstrapEvidence =
                requiresBootstrapEvidence(unit)
                        ? InvokeDynamicEvidenceIndex.build(unit, session)
                        : null;
        for (BoundChangePoint point : unit.getChangePoints().stream()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList()) {
            final ChangePoint change = point.getChangePoint();
            if (isAdded(change.getKind())) {
                dispositions.put(point,
                        ChangePointDisposition.CHANGE_KIND_NOT_ANALYZED);
                continue;
            }
            final List<Seed> seeds = resolveSeeds(
                    unit.getModuleId(), change, session,
                    bootstrapEvidence);
            if (seeds.isEmpty()) {
                dispositions.put(point, structuralDisposition(
                        point, change, structures));
                continue;
            }
            final int before = paths.size();
            for (Seed seed : seeds) {
                final ReverseTrace reverse = traceCache.computeIfAbsent(
                        seed.node(), node -> reverse(
                                unit.getModuleId(), node, session));
                paths.addAll(materialize(unit.getModuleId(), point,
                        seed, reverse, session));
            }
            dispositions.put(point, paths.size() > before
                    || structures.hasImpact(point)
                    ? ChangePointDisposition.IMPACT_REPORTED
                    : ChangePointDisposition.NO_PROJECT_PATH);
        }
        paths.sort(pathComparator());
        diagnostics.info(context, "candidatePaths=" + paths.size()
                + "; structuralImpacts=" + structures.impacts().size()
                + "; reverseBfs=" + traceCache.size());
        diagnostics.endStage(context);
        return new ModuleImpactQueryResult(
                paths, structures.impacts(), dispositions);
    }

    private List<Seed> resolveSeeds(
            final ModuleId moduleId,
            final ChangePoint point,
            final ModuleCallGraphSession session,
            final InvokeDynamicEvidenceIndex bootstrapEvidence) {
        final Set<Seed> result = new LinkedHashSet<>();
        if (point.getKind() == ChangePointKind.METHOD_BODY_CHANGED
                || point.getKind()
                == ChangePointKind.METHOD_DESCRIPTOR_CHANGED) {
            for (CGNode node : session.getGraph()) {
                if (matchesMethod(node.getMethod().getReference(),
                        point.getOwner(), point.getName(),
                        point.getNewDescriptor())) {
                    result.add(new Seed(queryNode(moduleId, node, session),
                            EdgeKind.METHOD_CHANGE,
                            "target=" + methodIdentity(
                                    node.getMethod().getReference())));
                }
            }
            for (QueryNode node
                    : session.getServiceLoaderOverlay().nodes()) {
                final MethodId method = node.methodId();
                if (point.getOwner().equals(method.owner())
                        && Objects.equals(point.getName(), method.name())
                        && Objects.equals(point.getNewDescriptor(),
                        method.descriptor())) {
                    result.add(new Seed(node, EdgeKind.METHOD_CHANGE,
                            "target=" + method));
                }
            }
        }
        if (point.getKind() == ChangePointKind.METHOD_REMOVED
                || point.getKind()
                == ChangePointKind.METHOD_DESCRIPTOR_CHANGED) {
            scanReachableIr(session, (node, instruction) -> {
                if (instruction instanceof SSAAbstractInvokeInstruction) {
                    final MethodReference target =
                            ((SSAAbstractInvokeInstruction) instruction)
                                    .getDeclaredTarget();
                    if (matchesMethod(target, point.getOwner(),
                            point.getName(), point.getOldDescriptor())) {
                        result.add(new Seed(
                                queryNode(moduleId, node, session),
                                EdgeKind.DECLARED_INVOKE_REFERENCE,
                                "declaredTarget=" + methodIdentity(target)));
                    }
                }
            });
            if (bootstrapEvidence != null) {
                for (BootstrapEvidence evidence : bootstrapEvidence.find(
                        point.getOwner(), point.getName(),
                        point.getOldDescriptor())) {
                    result.add(new Seed(queryNode(
                            moduleId, evidence.caller(), session),
                            EdgeKind.DECLARED_INVOKE_REFERENCE,
                            evidence.detail()));
                }
            }
        } else if (point.getKind() == ChangePointKind.FIELD_REMOVED
                || point.getKind()
                == ChangePointKind.FIELD_DESCRIPTOR_CHANGED) {
            scanReachableIr(session, (node, instruction) -> {
                if (instruction instanceof SSAFieldAccessInstruction) {
                    final FieldReference field =
                            ((SSAFieldAccessInstruction) instruction)
                                    .getDeclaredField();
                    if (matchesField(field, point)) {
                        result.add(new Seed(
                                queryNode(moduleId, node, session),
                                EdgeKind.FIELD_REFERENCE,
                                "declaredField=" + field));
                    }
                }
            });
        } else if (point.getKind() == ChangePointKind.CLASS_REMOVED) {
            scanReachableIr(session, (node, instruction) -> {
                if (referencesType(instruction, point.getOwner())) {
                    result.add(new Seed(
                            queryNode(moduleId, node, session),
                            EdgeKind.TYPE_REFERENCE,
                            "referencedType=" + point.getOwner()));
                }
            });
        }
        return result.stream().sorted(seedComparator()).toList();
    }

    private void scanReachableIr(
            final ModuleCallGraphSession session,
            final InstructionConsumer consumer) {
        for (CGNode node : session.getGraph()) {
            if (isSyntheticRoot(node, session)) {
                continue;
            }
            final IR ir = node.getIR();
            if (ir == null) {
                continue;
            }
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction != null) {
                    consumer.accept(node, instruction);
                }
            }
        }
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
            final Seed seed,
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
        final QueryEdge overlay = session.getServiceLoaderOverlay()
                .edge(caller, callee);
        if (overlay != null) {
            return overlay;
        }
        if (!(caller instanceof WalaQueryNode)
                || !(callee instanceof WalaQueryNode)) {
            throw new IllegalStateException(
                    "Missing overlay edge between synthetic nodes");
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
                ? "<jdk>" : ownership.getSource().toString();
        return new WalaQueryNode(node, new MethodId(owner,
                method.getName().toString(),
                method.getDescriptor().toString(), module, source),
                codeOrigin);
    }

    private boolean referencesType(
            final SSAInstruction instruction,
            final String expectedOwner) {
        if (instruction.getExceptionTypes().stream()
                .anyMatch(type -> matchesType(type, expectedOwner))) {
            return true;
        }
        if (instruction instanceof SSANewInstruction) {
            return matchesType(((SSANewInstruction) instruction)
                    .getConcreteType(), expectedOwner);
        }
        if (instruction instanceof SSACheckCastInstruction) {
            for (TypeReference type
                    :
                    ((SSACheckCastInstruction) instruction)
                            .getDeclaredResultTypes()) {
                if (matchesType(type, expectedOwner)) {
                    return true;
                }
            }
        }
        if (instruction instanceof SSAInstanceofInstruction) {
            return matchesType(((SSAInstanceofInstruction) instruction)
                    .getCheckedType(), expectedOwner);
        }
        if (instruction instanceof SSAArrayReferenceInstruction) {
            return matchesType(((SSAArrayReferenceInstruction) instruction)
                    .getElementType(), expectedOwner);
        }
        if (instruction instanceof SSALoadMetadataInstruction) {
            final SSALoadMetadataInstruction metadata =
                    (SSALoadMetadataInstruction) instruction;
            return matchesType(metadata.getType(), expectedOwner)
                    || metadata.getToken() instanceof TypeReference
                    && matchesType((TypeReference) metadata.getToken(),
                    expectedOwner);
        }
        if (instruction instanceof SSAAbstractInvokeInstruction) {
            return methodReferencesType(
                    ((SSAAbstractInvokeInstruction) instruction)
                            .getDeclaredTarget(), expectedOwner);
        }
        if (instruction instanceof SSAFieldAccessInstruction) {
            final FieldReference field =
                    ((SSAFieldAccessInstruction) instruction)
                            .getDeclaredField();
            return matchesType(field.getDeclaringClass(), expectedOwner)
                    || matchesType(field.getFieldType(), expectedOwner);
        }
        return false;
    }

    private boolean methodReferencesType(
            final MethodReference method,
            final String expectedOwner) {
        if (matchesType(method.getDeclaringClass(), expectedOwner)
                || matchesType(method.getReturnType(), expectedOwner)) {
            return true;
        }
        for (int index = 0;
                index < method.getNumberOfParameters(); index++) {
            if (matchesType(method.getParameterType(index), expectedOwner)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesType(
            final TypeReference type, final String expectedOwner) {
        if (type == null || type.isPrimitiveType()) {
            return false;
        }
        final TypeReference value = type.isArrayType()
                ? type.getInnermostElementType() : type;
        return expectedOwner.equals(owner(value));
    }

    private boolean matchesMethod(
            final MethodReference method,
            final String expectedOwner,
            final String expectedName,
            final String expectedDescriptor) {
        return method != null
                && expectedOwner.equals(owner(method))
                && Objects.equals(expectedName,
                        method.getName().toString())
                && Objects.equals(expectedDescriptor,
                        method.getDescriptor().toString());
    }

    private boolean matchesField(
            final FieldReference field, final ChangePoint point) {
        if (!point.getOwner().equals(owner(field.getDeclaringClass()))
                || !Objects.equals(point.getName(),
                        field.getName().toString())) {
            return false;
        }
        final String fieldDescriptor = descriptor(field.getFieldType());
        return Objects.equals(point.getOldDescriptor(), fieldDescriptor)
                || point.getKind()
                == ChangePointKind.FIELD_DESCRIPTOR_CHANGED
                && Objects.equals(point.getNewDescriptor(), fieldDescriptor);
    }

    private String descriptor(final TypeReference type) {
        final String value = type.getName().toString();
        if (value.startsWith("L") || value.startsWith("[L")) {
            return value.endsWith(";") ? value : value + ";";
        }
        return value;
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

    private boolean requiresBootstrapEvidence(
            final ModuleAnalysisUnit unit) {
        return unit.getChangePoints().stream().anyMatch(point ->
                point.getChangePoint().getKind()
                        == ChangePointKind.METHOD_REMOVED
                        || point.getChangePoint().getKind()
                        == ChangePointKind.METHOD_DESCRIPTOR_CHANGED);
    }

    private ChangePointDisposition missingDisposition(
            final ChangePoint point) {
        return point.getKind() == ChangePointKind.METHOD_BODY_CHANGED
                ? ChangePointDisposition.TARGET_NOT_FOUND
                : ChangePointDisposition.DECLARED_REFERENCE_NOT_FOUND;
    }

    private ChangePointDisposition structuralDisposition(
            final BoundChangePoint point,
            final ChangePoint change,
            final StructuralScanResult structures) {
        if (structures.hasImpact(point)) {
            return ChangePointDisposition.IMPACT_REPORTED;
        }
        if (structures.hasUnreachable(point)) {
            return ChangePointDisposition.UNREACHABLE_STRUCTURAL_REFERENCE;
        }
        return missingDisposition(change);
    }

    private Comparator<CGNode> nodeComparator() {
        return Comparator
                .comparing((CGNode node) -> methodIdentity(
                        node.getMethod().getReference()))
                .thenComparingInt(CGNode::getGraphNodeId);
    }

    private Comparator<Seed> seedComparator() {
        return Comparator.comparing((Seed seed) ->
                        methodIdentity(seed.node().methodId()))
                .thenComparingInt(seed -> queryNodeNumber(seed.node()))
                .thenComparing(seed -> seed.kind().name());
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
        final Set<QueryNode> result = new LinkedHashSet<>(
                session.getServiceLoaderOverlay().predecessors(node));
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
     * Seed retains exact Context and terminal evidence.
     *
     * @param node exact WALA node
     * @param kind terminal edge kind
     * @param evidence stable terminal evidence
     */
    private record Seed(QueryNode node, EdgeKind kind, String evidence) {
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

    /** Instruction callback. */
    @FunctionalInterface
    private interface InstructionConsumer {

        /**
         * Consumes one reachable instruction.
         *
         * @param node owning node
         * @param instruction instruction
         */
        void accept(CGNode node, SSAInstruction instruction);
    }
}
