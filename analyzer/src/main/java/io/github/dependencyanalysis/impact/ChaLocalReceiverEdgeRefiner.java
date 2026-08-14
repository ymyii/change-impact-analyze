package io.github.dependencyanalysis.impact;

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

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/** Query-scoped, fail-open CHA predecessor-edge refiner. */
final class ChaLocalReceiverEdgeRefiner {

    /** Maximum examples retained per Module. */
    private static final int EXAMPLE_LIMIT = 10;

    /** Frozen graph session. */
    private final ModuleCallGraphSession session;

    /** Effective application state. */
    private final ChaLocalReceiverRefinementSummary.Status status;

    /** Exact edge decisions shared by QueryNode workers. */
    private final ConcurrentMap<EdgeKey, EdgeOutcome> edgeCache =
            new ConcurrentHashMap<>();

    /** Caller IR and Def-Use facts shared by QueryNode workers. */
    private final ConcurrentMap<CGNode, CallerFacts> callerFacts =
            new ConcurrentHashMap<>();

    /** Receiver resolutions shared by QueryNode workers. */
    private final ConcurrentMap<CallerValue, ReceiverResolution>
            receiverCache = new ConcurrentHashMap<>();

    /** Deterministic smallest examples by stable key. */
    private final TreeMap<String,
            ChaLocalReceiverRefinementSummary.EdgeExample> examples =
            new TreeMap<>();

    /** Aggregate counters. */
    private final Counters counters = new Counters();

    /**
     * Creates one Module-query refiner.
     *
     * @param graphSession frozen graph session
     * @param selection command-wide selection
     */
    ChaLocalReceiverEdgeRefiner(
            final ModuleCallGraphSession graphSession,
            final ResultRefinementSelection selection) {
        session = graphSession;
        if (!selection.isEnabled(ResultRefinementAlgorithm
                .CHA_LOCAL_RECEIVER_INFERENCE)) {
            status = ChaLocalReceiverRefinementSummary.Status.NOT_SELECTED;
        } else if (session.getAlgorithm() != CallGraphAlgorithm.CHA) {
            status = ChaLocalReceiverRefinementSummary.Status
                    .NOT_APPLIED_NON_CHA;
        } else {
            status = ChaLocalReceiverRefinementSummary.Status.APPLIED;
        }
    }

    /**
     * Returns whether reverse BFS may traverse one original graph edge.
     *
     * @param caller original predecessor
     * @param callee current reverse-BFS node
     * @return true when the original edge must be retained
     */
    // Wiki: wiki/features/impact-tracing.md - Query-time CHA edge boundary
    boolean shouldTraverse(final CGNode caller, final CGNode callee) {
        if (status != ChaLocalReceiverRefinementSummary.Status.APPLIED) {
            return true;
        }
        interrupted();
        counters.predecessorEdgeRequests.increment();
        final EdgeKey key = new EdgeKey(caller, callee);
        final AtomicBoolean evaluated = new AtomicBoolean();
        final EdgeOutcome outcome = edgeCache.computeIfAbsent(key, ignored -> {
            evaluated.set(true);
            counters.uniqueEvaluatedEdges.increment();
            final EdgeOutcome result = evaluateSafely(caller, callee);
            record(result);
            return result;
        });
        if (!evaluated.get()) {
            counters.cacheHits.increment();
        }
        return outcome.decision() != EdgeDecision.PROVEN_INFEASIBLE;
    }

    private EdgeOutcome evaluateSafely(
            final CGNode caller,
            final CGNode callee) {
        try {
            return evaluate(caller, callee);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            return outcome(caller, callee, null,
                    EdgeDecision.RETAINED_UNKNOWN,
                    "resolution-failure-"
                            + exception.getClass().getSimpleName(),
                    "unknown");
        }
    }

    private EdgeOutcome evaluate(
            final CGNode caller,
            final CGNode callee) {
        final IR ir = callerFacts(caller).ir();
        if (ir == null) {
            return outcome(caller, callee, null,
                    EdgeDecision.NOT_APPLICABLE, "missing-ir", "unknown");
        }
        final List<CallSiteReference> sites = iteratorList(
                session.getGraph().getPossibleSites(caller, callee));
        sites.sort(Comparator.comparingInt(
                        CallSiteReference::getProgramCounter)
                .thenComparing(site -> site.getDeclaredTarget().toString()));
        if (sites.isEmpty()) {
            return outcome(caller, callee, null,
                    EdgeDecision.RETAINED_UNKNOWN, "missing-callsite",
                    "unknown");
        }
        EdgeOutcome representative = null;
        for (CallSiteReference site : sites) {
            interrupted();
            if (!site.isDispatch()) {
                return outcome(caller, callee, site,
                        EdgeDecision.NOT_APPLICABLE,
                        "fixed-dispatch", "unknown");
            }
            if (session.getGraph().getNumberOfTargets(caller, site) <= 1) {
                return outcome(caller, callee, site,
                        EdgeDecision.NOT_APPLICABLE,
                        "single-original-target", "unknown");
            }
            counters.callsitesChecked.increment();
            final SSAAbstractInvokeInstruction[] invokes = ir.getCalls(site);
            if (invokes == null || invokes.length == 0) {
                return outcome(caller, callee, site,
                        EdgeDecision.RETAINED_UNKNOWN,
                        "missing-invoke-instance", "unknown");
            }
            for (SSAAbstractInvokeInstruction invoke : invokes) {
                interrupted();
                counters.invokeInstancesChecked.increment();
                if (!invoke.isDispatch()) {
                    return outcome(caller, callee, site,
                            EdgeDecision.NOT_APPLICABLE,
                            "fixed-invoke-instance", "unknown");
                }
                final ReceiverResolution receiver = receiver(
                        caller, invoke.getReceiver());
                final DispatchDecision dispatch = dispatch(
                        receiver, invoke.getDeclaredTarget(), callee);
                representative = outcome(caller, callee, site,
                        dispatch.decision(), dispatch.reason(),
                        receiver.summary());
                if (dispatch.decision()
                        == EdgeDecision.RETAINED_FEASIBLE) {
                    return representative;
                }
                if (dispatch.decision()
                        == EdgeDecision.RETAINED_UNKNOWN) {
                    return representative;
                }
            }
        }
        return representative == null
                ? outcome(caller, callee, null,
                EdgeDecision.RETAINED_UNKNOWN, "no-invoke-evidence",
                "unknown")
                : new EdgeOutcome(EdgeDecision.PROVEN_INFEASIBLE,
                representative.example().caller(),
                representative.example().callee(),
                representative.example().programCounter(),
                representative.example().invocationKind(),
                "all-call-sites-exclude-callee",
                representative.example().receiverSummary());
    }

    private DispatchDecision dispatch(
            final ReceiverResolution receiver,
            final MethodReference declaredTarget,
            final CGNode callee) {
        if (receiver.kind() == ReceiverKind.UNKNOWN) {
            return new DispatchDecision(EdgeDecision.RETAINED_UNKNOWN,
                    "receiver-unknown");
        }
        if (receiver.kind() == ReceiverKind.NO_NORMAL_TARGET) {
            return new DispatchDecision(EdgeDecision.PROVEN_INFEASIBLE,
                    "receiver-has-no-normal-target");
        }
        final IClassHierarchy hierarchy = session.getHierarchy();
        final MethodReference calleeReference =
                callee.getMethod().getReference();
        if (receiver.kind() == ReceiverKind.EXACT) {
            MethodReference commonTarget = null;
            for (TypeReference type : receiver.types()) {
                interrupted();
                final IClass receiverClass = hierarchy.lookupClass(type);
                if (receiverClass == null || receiverClass.isInterface()
                        || receiverClass.isAbstract()) {
                    return new DispatchDecision(
                            EdgeDecision.RETAINED_UNKNOWN,
                            "exact-type-unresolved-or-abstract");
                }
                final IMethod target = hierarchy.resolveMethod(
                        receiverClass, declaredTarget.getSelector());
                if (target == null || target.isAbstract()) {
                    return new DispatchDecision(
                            EdgeDecision.RETAINED_UNKNOWN,
                            "exact-target-unresolved-or-abstract");
                }
                if (commonTarget == null) {
                    commonTarget = target.getReference();
                } else if (!commonTarget.equals(target.getReference())) {
                    return new DispatchDecision(
                            EdgeDecision.RETAINED_UNKNOWN,
                            "exact-targets-inconsistent");
                }
            }
            return calleeReference.equals(commonTarget)
                    ? new DispatchDecision(
                    EdgeDecision.RETAINED_FEASIBLE,
                    "exact-dispatch-reaches-callee")
                    : new DispatchDecision(
                    EdgeDecision.PROVEN_INFEASIBLE,
                    "exact-dispatch-excludes-callee");
        }
        final TypeReference bound = receiver.types().iterator().next();
        final IClass boundClass = hierarchy.lookupClass(bound);
        if (boundClass == null) {
            return new DispatchDecision(EdgeDecision.RETAINED_UNKNOWN,
                    "upper-bound-unresolved");
        }
        final Set<IMethod> targets = boundClass.isInterface()
                ? hierarchy.getPossibleTargets(MethodReference.findOrCreate(
                bound, declaredTarget.getSelector()))
                : hierarchy.getPossibleTargets(boundClass, declaredTarget);
        if (targets == null || targets.isEmpty()
                || targets.stream().anyMatch(IMethod::isAbstract)) {
            return new DispatchDecision(EdgeDecision.RETAINED_UNKNOWN,
                    "upper-bound-targets-incomplete");
        }
        return targets.stream().map(IMethod::getReference)
                .anyMatch(calleeReference::equals)
                ? new DispatchDecision(EdgeDecision.RETAINED_FEASIBLE,
                "upper-bound-includes-callee")
                : new DispatchDecision(EdgeDecision.PROVEN_INFEASIBLE,
                "upper-bound-excludes-callee");
    }

    private ReceiverResolution receiver(
            final CGNode caller,
            final int valueNumber) {
        final CallerValue key = new CallerValue(caller, valueNumber);
        return receiverCache.computeIfAbsent(key, ignored -> {
            final ReceiverResolution result = resolveReceiver(
                    callerFacts(caller), valueNumber);
            switch (result.kind()) {
                case EXACT -> counters.exactResolutions.increment();
                case UPPER_BOUND -> counters.upperBoundResolutions.increment();
                case NO_NORMAL_TARGET ->
                        counters.noNormalTargetResolutions.increment();
                case UNKNOWN -> counters.unknownResolutions.increment();
                default -> throw new IllegalStateException(
                        "Unhandled receiver kind: " + result.kind());
            }
            return result;
        });
    }

    private ReceiverResolution resolveReceiver(
            final CallerFacts facts,
            final int rootValue) {
        if (facts.ir() == null || facts.defUse() == null) {
            return ReceiverResolution.unknown();
        }
        final Map<Integer, ReceiverResolution> resolved = new HashMap<>();
        final Set<Integer> active = new HashSet<>();
        final Deque<ValueFrame> work = new ArrayDeque<>();
        work.push(new ValueFrame(rootValue, false));
        while (!work.isEmpty()) {
            interrupted();
            final ValueFrame frame = work.peek();
            final ReceiverResolution complete = resolved.get(frame.value());
            if (complete != null) {
                work.pop();
                active.remove(frame.value());
                continue;
            }
            final ReceiverResolution cached = receiverCache.get(
                    new CallerValue(facts.caller(), frame.value()));
            if (cached != null) {
                resolved.put(frame.value(), cached);
                continue;
            }
            if (!frame.expanded()) {
                final ReceiverResolution base = baseResolution(
                        facts, frame.value());
                if (base != null) {
                    resolved.put(frame.value(), base);
                    continue;
                }
                final SSAInstruction definition = facts.defUse()
                        .getDef(frame.value());
                if (!supportedComposition(definition)) {
                    resolved.put(frame.value(), ReceiverResolution.unknown());
                    continue;
                }
                work.pop();
                work.push(new ValueFrame(frame.value(), true));
                active.add(frame.value());
                final List<Integer> dependencies = dependencies(definition);
                boolean cycle = false;
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
                            && !receiverCache.containsKey(new CallerValue(
                            facts.caller(), dependency))) {
                        work.push(new ValueFrame(dependency, false));
                    }
                }
                if (cycle) {
                    resolved.put(frame.value(), ReceiverResolution.unknown());
                }
                continue;
            }
            final SSAInstruction definition = facts.defUse()
                    .getDef(frame.value());
            resolved.put(frame.value(), combine(facts, definition, resolved));
        }
        resolved.forEach((value, resolution) -> {
            if (value != rootValue) {
                receiverCache.putIfAbsent(
                        new CallerValue(facts.caller(), value), resolution);
            }
        });
        return resolved.getOrDefault(rootValue, ReceiverResolution.unknown());
    }

    private ReceiverResolution baseResolution(
            final CallerFacts facts,
            final int value) {
        final SymbolTable symbols = facts.ir().getSymbolTable();
        if (value < 1) {
            return ReceiverResolution.unknown();
        }
        if (symbols.isNullConstant(value)) {
            return ReceiverResolution.noNormalTarget();
        }
        if (symbols.isParameter(value) || symbols.isConstant(value)) {
            return ReceiverResolution.unknown();
        }
        final SSAInstruction definition = facts.defUse().getDef(value);
        if (definition instanceof SSANewInstruction allocation) {
            final TypeReference type = allocation.getConcreteType();
            if (type == null || type.isArrayType()
                    || !type.isReferenceType()) {
                return ReceiverResolution.unknown();
            }
            return ReceiverResolution.exact(Set.of(type));
        }
        return definition == null ? ReceiverResolution.unknown() : null;
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

    private ReceiverResolution combine(
            final CallerFacts facts,
            final SSAInstruction definition,
            final Map<Integer, ReceiverResolution> resolved) {
        if (definition instanceof SSAPiInstruction pi) {
            return dependency(facts, resolved, pi.getVal());
        }
        if (definition instanceof SSACheckCastInstruction cast) {
            return applyCast(cast,
                    dependency(facts, resolved, cast.getVal()));
        }
        if (definition instanceof SSAPhiInstruction phi) {
            return mergePhi(facts, phi, resolved);
        }
        return ReceiverResolution.unknown();
    }

    private ReceiverResolution dependency(
            final CallerFacts facts,
            final Map<Integer, ReceiverResolution> resolved,
            final int value) {
        return resolved.getOrDefault(value, receiverCache.getOrDefault(
                new CallerValue(facts.caller(), value),
                ReceiverResolution.unknown()));
    }

    private ReceiverResolution applyCast(
            final SSACheckCastInstruction cast,
            final ReceiverResolution input) {
        final TypeReference[] types = cast.getDeclaredResultTypes();
        if (types == null || types.length != 1 || types[0] == null
                || types[0].isArrayType() || !types[0].isReferenceType()) {
            return ReceiverResolution.unknown();
        }
        final TypeReference bound = types[0];
        final IClass boundClass = session.getHierarchy().lookupClass(bound);
        if (boundClass == null) {
            return ReceiverResolution.unknown();
        }
        if (input.kind() == ReceiverKind.NO_NORMAL_TARGET) {
            return input;
        }
        if (input.kind() != ReceiverKind.EXACT) {
            return ReceiverResolution.upperBound(bound);
        }
        final Set<TypeReference> compatible = new LinkedHashSet<>();
        for (TypeReference exact : input.types()) {
            final IClass exactClass = session.getHierarchy()
                    .lookupClass(exact);
            if (exactClass == null) {
                return ReceiverResolution.unknown();
            }
            if (session.getHierarchy().isAssignableFrom(
                    boundClass, exactClass)) {
                compatible.add(exact);
            }
        }
        return compatible.isEmpty()
                ? ReceiverResolution.noNormalTarget()
                : ReceiverResolution.exact(compatible);
    }

    private ReceiverResolution mergePhi(
            final CallerFacts facts,
            final SSAPhiInstruction phi,
            final Map<Integer, ReceiverResolution> resolved) {
        final Set<TypeReference> exact = new LinkedHashSet<>();
        boolean upper = false;
        boolean normal = false;
        for (int index = 0; index < phi.getNumberOfUses(); index++) {
            final int value = phi.getUse(index);
            if (value < 1) {
                continue;
            }
            final ReceiverResolution input = dependency(
                    facts, resolved, value);
            if (input.kind() == ReceiverKind.UNKNOWN) {
                return ReceiverResolution.unknown();
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
            return ReceiverResolution.noNormalTarget();
        }
        if (upper) {
            return ReceiverResolution.unknown();
        }
        return exact.isEmpty() ? ReceiverResolution.unknown()
                : ReceiverResolution.exact(exact);
    }

    private CallerFacts callerFacts(final CGNode caller) {
        return callerFacts.computeIfAbsent(caller, value -> {
            final IR ir = value.getIR();
            return ir == null ? new CallerFacts(value, null, null)
                    : new CallerFacts(value, ir, new DefUse(ir));
        });
    }

    private EdgeOutcome outcome(
            final CGNode caller,
            final CGNode callee,
            final CallSiteReference site,
            final EdgeDecision decision,
            final String reason,
            final String receiverSummary) {
        return new EdgeOutcome(decision, nodeIdentity(caller),
                nodeIdentity(callee), site == null ? -1
                : site.getProgramCounter(), site == null ? "unknown"
                : site.getInvocationString(), reason, receiverSummary);
    }

    private String nodeIdentity(final CGNode node) {
        return node.getMethod().getReference() + "@"
                + node.getGraphNodeId();
    }

    private void record(final EdgeOutcome outcome) {
        switch (outcome.decision()) {
            case PROVEN_INFEASIBLE -> counters.prunedEdges.increment();
            case RETAINED_FEASIBLE ->
                    counters.retainedFeasibleEdges.increment();
            case RETAINED_UNKNOWN -> counters.retainedUnknownEdges.increment();
            case NOT_APPLICABLE -> counters.notApplicableEdges.increment();
            default -> throw new IllegalStateException(
                    "Unhandled edge decision: " + outcome.decision());
        }
        final ChaLocalReceiverRefinementSummary.EdgeExample example =
                outcome.example();
        synchronized (examples) {
            examples.put(example.stableKey(), example);
            while (examples.size() > EXAMPLE_LIMIT) {
                examples.pollLastEntry();
            }
        }
    }

    /** @return immutable execution summary */
    ChaLocalReceiverRefinementSummary summary() {
        final List<ChaLocalReceiverRefinementSummary.EdgeExample> stable;
        synchronized (examples) {
            stable = List.copyOf(examples.values());
        }
        return new ChaLocalReceiverRefinementSummary(status,
                counters.metrics(), stable);
    }

    private void interrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new ImpactException(
                    "CHA local receiver inference interrupted");
        }
    }

    private <T> List<T> iteratorList(final Iterator<T> values) {
        final List<T> result = new ArrayList<>();
        values.forEachRemaining(result::add);
        return result;
    }

    /**
     * Edge-cache identity.
     *
     * @param caller exact caller node
     * @param callee exact callee node
     */
    private record EdgeKey(CGNode caller, CGNode callee) {
    }

    /**
     * Receiver-cache identity.
     *
     * @param caller exact caller node
     * @param value SSA value number
     */
    private record CallerValue(CGNode caller, int value) {
    }

    /**
     * Immutable caller analysis facts.
     *
     * @param caller exact caller node
     * @param ir caller IR
     * @param defUse caller definition-use facts
     */
    private record CallerFacts(CGNode caller, IR ir, DefUse defUse) {
    }

    /**
     * Explicit iterative receiver-resolution frame.
     *
     * @param value SSA value number
     * @param expanded whether dependencies have been scheduled
     */
    private record ValueFrame(int value, boolean expanded) {
    }

    /** Edge outcome categories. */
    private enum EdgeDecision {
        /** All associated invokes exclude the callee. */
        PROVEN_INFEASIBLE("proven-infeasible"),

        /** At least one invoke can reach the callee. */
        RETAINED_FEASIBLE("retained-feasible"),

        /** Proof is incomplete. */
        RETAINED_UNKNOWN("retained-unknown"),

        /** Edge is outside the supported inference gate. */
        NOT_APPLICABLE("not-applicable");

        /** Stable decision identifier. */
        private final String identifier;

        EdgeDecision(final String stableIdentifier) {
            identifier = stableIdentifier;
        }

        String identifier() {
            return identifier;
        }
    }

    /**
     * Stable edge outcome and example source.
     *
     * @param decision edge decision
     * @param caller stable caller identity
     * @param callee stable callee identity
     * @param programCounter callsite bytecode program counter
     * @param invocationKind invocation kind
     * @param reason stable decision reason
     * @param receiverSummary stable receiver summary
     */
    private record EdgeOutcome(
            EdgeDecision decision,
            String caller,
            String callee,
            int programCounter,
            String invocationKind,
            String reason,
            String receiverSummary) {

        ChaLocalReceiverRefinementSummary.EdgeExample example() {
            return new ChaLocalReceiverRefinementSummary.EdgeExample(
                    caller, callee, programCounter, invocationKind,
                    decision.identifier(), reason, receiverSummary);
        }
    }

    /**
     * Dispatch proof.
     *
     * @param decision edge decision
     * @param reason stable decision reason
     */
    private record DispatchDecision(
            EdgeDecision decision,
            String reason) {
    }

    /** Receiver resolution categories. */
    private enum ReceiverKind {
        /** Finite exact runtime types. */
        EXACT,

        /** One cast-derived type upper bound. */
        UPPER_BOUND,

        /** Explicit null or impossible exact cast. */
        NO_NORMAL_TARGET,

        /** Unsupported or incomplete receiver evidence. */
        UNKNOWN
    }

    /**
     * Immutable receiver result.
     *
     * @param kind resolution category
     * @param types exact types or one upper-bound type
     */
    private record ReceiverResolution(
            ReceiverKind kind,
            Set<TypeReference> types) {

        static ReceiverResolution exact(final Set<TypeReference> values) {
            return new ReceiverResolution(ReceiverKind.EXACT,
                    Set.copyOf(values));
        }

        static ReceiverResolution upperBound(final TypeReference value) {
            return new ReceiverResolution(ReceiverKind.UPPER_BOUND,
                    Set.of(value));
        }

        static ReceiverResolution noNormalTarget() {
            return new ReceiverResolution(ReceiverKind.NO_NORMAL_TARGET,
                    Set.of());
        }

        static ReceiverResolution unknown() {
            return new ReceiverResolution(ReceiverKind.UNKNOWN, Set.of());
        }

        String summary() {
            if (types.isEmpty()) {
                return kind.name().toLowerCase().replace('_', '-');
            }
            return kind.name().toLowerCase().replace('_', '-') + ":"
                    + types.stream().map(TypeReference::toString)
                    .sorted().collect(java.util.stream.Collectors
                            .joining(","));
        }
    }

    /** Thread-safe aggregate counters. */
    private static final class Counters {
        /** Edge validation requests. */
        private final LongAdder predecessorEdgeRequests = new LongAdder();
        /** Unique evaluated edges. */
        private final LongAdder uniqueEvaluatedEdges = new LongAdder();
        /** Exact edge-cache hits. */
        private final LongAdder cacheHits = new LongAdder();
        /** Dynamic callsites checked. */
        private final LongAdder callsitesChecked = new LongAdder();
        /** Invoke instances checked. */
        private final LongAdder invokeInstancesChecked = new LongAdder();
        /** Proven-infeasible edges. */
        private final LongAdder prunedEdges = new LongAdder();
        /** Proven-feasible edges. */
        private final LongAdder retainedFeasibleEdges = new LongAdder();
        /** Unknown edges retained. */
        private final LongAdder retainedUnknownEdges = new LongAdder();
        /** Edges outside the inference gate. */
        private final LongAdder notApplicableEdges = new LongAdder();
        /** Exact receiver resolutions. */
        private final LongAdder exactResolutions = new LongAdder();
        /** Upper-bound receiver resolutions. */
        private final LongAdder upperBoundResolutions = new LongAdder();
        /** Receivers without normal targets. */
        private final LongAdder noNormalTargetResolutions = new LongAdder();
        /** Unknown receiver resolutions. */
        private final LongAdder unknownResolutions = new LongAdder();

        ChaLocalReceiverRefinementSummary.Metrics metrics() {
            return new ChaLocalReceiverRefinementSummary.Metrics(
                    predecessorEdgeRequests.sum(),
                    uniqueEvaluatedEdges.sum(), cacheHits.sum(),
                    callsitesChecked.sum(), invokeInstancesChecked.sum(),
                    prunedEdges.sum(), retainedFeasibleEdges.sum(),
                    retainedUnknownEdges.sum(), notApplicableEdges.sum(),
                    exactResolutions.sum(), upperBoundResolutions.sum(),
                    noNormalTargetResolutions.sum(),
                    unknownResolutions.sum());
        }
    }
}
