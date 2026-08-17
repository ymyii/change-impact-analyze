package io.github.dependencyanalysis.impact.pruning.cha;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.impact.ImpactException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cached CHA caller-local receiver, IR, callsite and dispatch resolver. */
final class ChaReceiverTypeResolver {

    /** Frozen graph session. */
    private final ModuleCallGraphSession session;

    /** IR and Def-Use facts shared by extension workers. */
    private final ConcurrentMap<CGNode, NodeFacts> facts =
            new ConcurrentHashMap<>();

    /** Local receiver results shared by both extensions. */
    private final ConcurrentMap<NodeValue, Resolution> receivers =
            new ConcurrentHashMap<>();

    /** Stable possible-sites cache. */
    private final ConcurrentMap<NodeEdge, List<CallSiteReference>> sites =
            new ConcurrentHashMap<>();

    /** JVM dispatch resolution cache. */
    private final ConcurrentMap<DispatchKey, DispatchResult> dispatch =
            new ConcurrentHashMap<>();

    ChaReceiverTypeResolver(final ModuleCallGraphSession graphSession) {
        session = graphSession;
    }

    ModuleCallGraphSession session() {
        return session;
    }

    NodeFacts facts(final CGNode node) {
        return facts.computeIfAbsent(node, value -> {
            final IR ir = value.getIR();
            return ir == null ? new NodeFacts(value, null, null)
                    : new NodeFacts(value, ir, new DefUse(ir));
        });
    }

    List<CallSiteReference> sites(final CGNode caller, final CGNode callee) {
        return sites.computeIfAbsent(new NodeEdge(caller, callee), ignored -> {
            final List<CallSiteReference> result = iteratorList(
                    session.getGraph().getPossibleSites(caller, callee));
            result.sort(Comparator.comparingInt(
                            CallSiteReference::getProgramCounter)
                    .thenComparing(site -> site.getDeclaredTarget()
                            .toString()));
            return List.copyOf(result);
        });
    }

    SSAAbstractInvokeInstruction[] invokes(
            final CGNode caller,
            final CallSiteReference site) {
        final IR ir = facts(caller).ir();
        if (ir == null || ir.getCallInstructionIndices(site) == null) {
            return new SSAAbstractInvokeInstruction[0];
        }
        final SSAAbstractInvokeInstruction[] result = ir.getCalls(site);
        return result == null ? new SSAAbstractInvokeInstruction[0] : result;
    }

    Resolution receiver(final CGNode node, final int valueNumber) {
        final NodeValue key = new NodeValue(node, valueNumber);
        return receivers.computeIfAbsent(key, ignored ->
                resolveReceiver(facts(node), valueNumber));
    }

    DispatchResult dispatch(
            final Resolution receiver,
            final MethodReference declaredTarget,
            final CGNode candidate) {
        return dispatch.computeIfAbsent(new DispatchKey(receiver,
                declaredTarget, candidate.getMethod().getReference()),
                ignored -> resolveDispatch(receiver, declaredTarget,
                        candidate.getMethod().getReference()));
    }

    private DispatchResult resolveDispatch(
            final Resolution receiver,
            final MethodReference declaredTarget,
            final MethodReference candidate) {
        interrupted();
        if (receiver.kind() == ReceiverKind.UNKNOWN) {
            return DispatchResult.unknown("receiver-unknown");
        }
        if (receiver.kind() == ReceiverKind.NO_NORMAL_TARGET) {
            return DispatchResult.excludes("receiver-has-no-normal-target");
        }
        final IClassHierarchy hierarchy = session.getHierarchy();
        if (receiver.kind() == ReceiverKind.EXACT) {
            for (TypeReference type : receiver.types()) {
                interrupted();
                final IClass receiverClass = hierarchy.lookupClass(type);
                if (receiverClass == null || receiverClass.isInterface()
                        || receiverClass.isAbstract()) {
                    return DispatchResult.unknown(
                            "exact-type-unresolved-or-abstract");
                }
                final IMethod target = hierarchy.resolveMethod(
                        receiverClass, declaredTarget.getSelector());
                if (target == null || target.isAbstract()) {
                    return DispatchResult.unknown(
                            "exact-target-unresolved-or-abstract");
                }
                if (candidate.equals(target.getReference())) {
                    return DispatchResult.reaches(
                            "exact-dispatch-reaches-callee");
                }
            }
            return DispatchResult.excludes(
                    "exact-dispatch-excludes-callee");
        }
        final TypeReference bound = receiver.types().iterator().next();
        final IClass boundClass = hierarchy.lookupClass(bound);
        if (boundClass == null) {
            return DispatchResult.unknown("upper-bound-unresolved");
        }
        final Set<IMethod> possible = boundClass.isInterface()
                ? hierarchy.getPossibleTargets(MethodReference.findOrCreate(
                bound, declaredTarget.getSelector()))
                : hierarchy.getPossibleTargets(boundClass, declaredTarget);
        if (possible == null || possible.isEmpty()
                || possible.stream().anyMatch(IMethod::isAbstract)) {
            return DispatchResult.unknown("upper-bound-targets-incomplete");
        }
        return possible.stream().map(IMethod::getReference)
                .anyMatch(candidate::equals)
                ? DispatchResult.reaches("upper-bound-includes-callee")
                : DispatchResult.excludes("upper-bound-excludes-callee");
    }

    private Resolution resolveReceiver(
            final NodeFacts nodeFacts,
            final int rootValue) {
        if (nodeFacts.ir() == null || nodeFacts.defUse() == null) {
            return Resolution.unknown();
        }
        final Map<Integer, Resolution> resolved = new HashMap<>();
        final Set<Integer> active = new HashSet<>();
        final Deque<ValueFrame> work = new ArrayDeque<>();
        work.push(new ValueFrame(rootValue, false));
        while (!work.isEmpty()) {
            interrupted();
            final ValueFrame frame = work.peek();
            final Resolution complete = resolved.get(frame.value());
            if (complete != null) {
                work.pop();
                active.remove(frame.value());
                continue;
            }
            final Resolution cached = receivers.get(
                    new NodeValue(nodeFacts.node(), frame.value()));
            if (cached != null) {
                resolved.put(frame.value(), cached);
                continue;
            }
            if (!frame.expanded()) {
                final Resolution base = baseResolution(
                        nodeFacts, frame.value());
                if (base != null) {
                    resolved.put(frame.value(), base);
                    continue;
                }
                final SSAInstruction definition = nodeFacts.defUse()
                        .getDef(frame.value());
                if (!supportedComposition(definition)) {
                    resolved.put(frame.value(), Resolution.unknown());
                    continue;
                }
                work.pop();
                work.push(new ValueFrame(frame.value(), true));
                active.add(frame.value());
                boolean cycle = false;
                final List<Integer> dependencies = dependencies(definition);
                for (int index = dependencies.size() - 1;
                        index >= 0; index--) {
                    final int dependency = dependencies.get(index);
                    if (dependency < 1) {
                        continue;
                    }
                    if (active.contains(dependency)) {
                        cycle = true;
                        break;
                    }
                    if (!resolved.containsKey(dependency)
                            && !receivers.containsKey(new NodeValue(
                            nodeFacts.node(), dependency))) {
                        work.push(new ValueFrame(dependency, false));
                    }
                }
                if (cycle) {
                    resolved.put(frame.value(), Resolution.unknown());
                }
                continue;
            }
            final SSAInstruction definition = nodeFacts.defUse()
                    .getDef(frame.value());
            resolved.put(frame.value(), combine(
                    nodeFacts, definition, resolved));
        }
        resolved.forEach((value, resolution) -> {
            if (value != rootValue) {
                receivers.putIfAbsent(new NodeValue(
                        nodeFacts.node(), value), resolution);
            }
        });
        return resolved.getOrDefault(rootValue, Resolution.unknown());
    }

    private Resolution baseResolution(
            final NodeFacts nodeFacts,
            final int value) {
        final SymbolTable symbols = nodeFacts.ir().getSymbolTable();
        if (value < 1) {
            return Resolution.unknown();
        }
        if (symbols.isNullConstant(value)) {
            return Resolution.noNormalTarget();
        }
        if (symbols.isParameter(value) || symbols.isConstant(value)) {
            return Resolution.unknown();
        }
        final SSAInstruction definition = nodeFacts.defUse().getDef(value);
        if (definition instanceof SSANewInstruction allocation) {
            final TypeReference type = allocation.getConcreteType();
            if (type == null || type.isArrayType()
                    || !type.isReferenceType()) {
                return Resolution.unknown();
            }
            return Resolution.exact(Set.of(type));
        }
        return definition == null ? Resolution.unknown() : null;
    }

    private boolean supportedComposition(final SSAInstruction definition) {
        return definition instanceof SSAPhiInstruction
                || definition instanceof SSAPiInstruction
                || definition instanceof SSACheckCastInstruction;
    }

    private List<Integer> dependencies(final SSAInstruction definition) {
        if (definition instanceof SSAPiInstruction pi) {
            return List.of(pi.getVal());
        }
        if (definition instanceof SSACheckCastInstruction cast) {
            return List.of(cast.getVal());
        }
        final List<Integer> result = new ArrayList<>();
        for (int index = 0; index < definition.getNumberOfUses(); index++) {
            result.add(definition.getUse(index));
        }
        return result;
    }

    private Resolution combine(
            final NodeFacts nodeFacts,
            final SSAInstruction definition,
            final Map<Integer, Resolution> resolved) {
        if (definition instanceof SSAPiInstruction pi) {
            return dependency(nodeFacts, resolved, pi.getVal());
        }
        if (definition instanceof SSACheckCastInstruction cast) {
            return applyCast(cast,
                    dependency(nodeFacts, resolved, cast.getVal()));
        }
        if (definition instanceof SSAPhiInstruction phi) {
            return mergePhi(nodeFacts, phi, resolved);
        }
        return Resolution.unknown();
    }

    private Resolution dependency(
            final NodeFacts nodeFacts,
            final Map<Integer, Resolution> resolved,
            final int value) {
        return resolved.getOrDefault(value, receivers.getOrDefault(
                new NodeValue(nodeFacts.node(), value),
                Resolution.unknown()));
    }

    private Resolution applyCast(
            final SSACheckCastInstruction cast,
            final Resolution input) {
        final TypeReference[] types = cast.getDeclaredResultTypes();
        if (types == null || types.length != 1 || types[0] == null
                || types[0].isArrayType() || !types[0].isReferenceType()) {
            return Resolution.unknown();
        }
        final TypeReference bound = types[0];
        final IClass boundClass = session.getHierarchy().lookupClass(bound);
        if (boundClass == null) {
            return Resolution.unknown();
        }
        if (input.kind() == ReceiverKind.NO_NORMAL_TARGET) {
            return input;
        }
        if (input.kind() != ReceiverKind.EXACT) {
            return Resolution.upperBound(bound);
        }
        final Set<TypeReference> compatible = new LinkedHashSet<>();
        for (TypeReference exact : input.types()) {
            final IClass exactClass = session.getHierarchy().lookupClass(exact);
            if (exactClass == null) {
                return Resolution.unknown();
            }
            if (session.getHierarchy().isAssignableFrom(
                    boundClass, exactClass)) {
                compatible.add(exact);
            }
        }
        return compatible.isEmpty()
                ? Resolution.noNormalTarget()
                : Resolution.exact(compatible);
    }

    private Resolution mergePhi(
            final NodeFacts nodeFacts,
            final SSAPhiInstruction phi,
            final Map<Integer, Resolution> resolved) {
        final Set<TypeReference> exact = new LinkedHashSet<>();
        boolean upper = false;
        boolean normal = false;
        for (int index = 0; index < phi.getNumberOfUses(); index++) {
            final int value = phi.getUse(index);
            if (value < 1) {
                continue;
            }
            final Resolution input = dependency(nodeFacts, resolved, value);
            if (input.kind() == ReceiverKind.UNKNOWN) {
                return Resolution.unknown();
            }
            if (input.kind() == ReceiverKind.NO_NORMAL_TARGET) {
                continue;
            }
            normal = true;
            if (input.kind() == ReceiverKind.UPPER_BOUND) {
                upper = true;
            } else {
                exact.addAll(input.types());
            }
        }
        if (!normal) {
            return Resolution.noNormalTarget();
        }
        if (upper) {
            return Resolution.unknown();
        }
        return exact.isEmpty() ? Resolution.unknown()
                : Resolution.exact(exact);
    }

    private void interrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new ImpactException("CHA receiver inference interrupted");
        }
    }

    private static <T> List<T> iteratorList(final Iterator<T> values) {
        final List<T> result = new ArrayList<>();
        values.forEachRemaining(result::add);
        return result;
    }

    static String nodeIdentity(final CGNode node) {
        return node == null ? "<seed>" : node.getMethod().getReference()
                + "@" + node.getGraphNodeId();
    }

    enum ReceiverKind {
        /** Fully known concrete receiver types. */
        EXACT,
        /** Conservative receiver upper bound. */
        UPPER_BOUND,
        /** Null or otherwise no normal dispatch target. */
        NO_NORMAL_TARGET,
        /** Incomplete receiver evidence. */
        UNKNOWN
    }

    record Resolution(ReceiverKind kind, Set<TypeReference> types) {
        Resolution {
            types = Set.copyOf(types);
        }

        static Resolution exact(final Set<TypeReference> values) {
            return new Resolution(ReceiverKind.EXACT, values);
        }

        static Resolution upperBound(final TypeReference value) {
            return new Resolution(ReceiverKind.UPPER_BOUND, Set.of(value));
        }

        static Resolution noNormalTarget() {
            return new Resolution(ReceiverKind.NO_NORMAL_TARGET, Set.of());
        }

        static Resolution unknown() {
            return new Resolution(ReceiverKind.UNKNOWN, Set.of());
        }

        String summary() {
            if (types.isEmpty()) {
                return kind.name().toLowerCase().replace('_', '-');
            }
            return kind.name().toLowerCase().replace('_', '-') + ":"
                    + types.stream().map(TypeReference::toString).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
        }
    }

    enum DispatchReachability {
        /** Candidate is reachable for at least one receiver type. */
        REACHES,
        /** Candidate is excluded for all receiver types. */
        EXCLUDES,
        /** Dispatch cannot be decided safely. */
        UNKNOWN
    }

    record DispatchResult(DispatchReachability reachability, String reason) {
        static DispatchResult reaches(final String reason) {
            return new DispatchResult(DispatchReachability.REACHES, reason);
        }

        static DispatchResult excludes(final String reason) {
            return new DispatchResult(DispatchReachability.EXCLUDES, reason);
        }

        static DispatchResult unknown(final String reason) {
            return new DispatchResult(DispatchReachability.UNKNOWN, reason);
        }
    }

    record NodeFacts(CGNode node, IR ir, DefUse defUse) {
    }

    private record NodeValue(CGNode node, int value) {
    }

    private record NodeEdge(CGNode caller, CGNode callee) {
    }

    private record DispatchKey(
            Resolution receiver,
            MethodReference declaredTarget,
            MethodReference candidate) {
    }

    private record ValueFrame(int value, boolean expanded) {
    }
}
