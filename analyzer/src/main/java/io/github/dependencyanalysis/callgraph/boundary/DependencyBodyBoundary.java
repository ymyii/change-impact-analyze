package io.github.dependencyanalysis.callgraph.boundary;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.scope.CallGraphChange;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyScope;
import io.github.dependencyanalysis.classpath.ClassOwnership;
import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.scope.DependencyBodyPolicy;
import io.github.dependencyanalysis.callgraph.scope.DependencyScopeMode;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.IntSet;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Shared no-op, flow-to-cast, and dangerous-transfer fixed-point model. */
public final class DependencyBodyBoundary {

    /** Maximum phi/pi/definition hops used for local type recovery. */
    private static final int MAX_FLOW_HOPS = 8;

    /** Context key carrying factory target types. */
    private enum FactoryContextKey implements ContextKey {
        /** Factory target facts. */
        INSTANCE
    }

    /**
     * Factory fact carried by a callee Context.
     *
     * @param types inferred allocation types
     * @param castInstructions matching cast instruction indexes
     * @param caller caller method
     * @param bytecodePc invoke bytecode program counter
     */
    private record FactoryFact(
            List<TypeReference> types,
            List<Integer> castInstructions,
            String caller,
            int bytecodePc) implements ContextItem {

        FactoryFact {
            types = List.copyOf(types);
            castInstructions = List.copyOf(castInstructions);
            Objects.requireNonNull(caller, "caller");
        }
    }

    /**
     * Delegated algorithm Context plus factory facts.
     *
     * @param base algorithm Context
     * @param fact factory facts
     */
    private record FactoryContext(
            Context base,
            FactoryFact fact) implements Context {

        FactoryContext {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(fact, "fact");
        }

        @Override
        public ContextItem get(final ContextKey key) {
            return key == FactoryContextKey.INSTANCE ? fact : base.get(key);
        }
    }

    /** Class ownership used for logical artifact decisions. */
    private final ClassOwnershipIndex ownership;

    /** Target class hierarchy. */
    private final IClassHierarchy hierarchy;

    /** Planned dependency paths and artifact policies. */
    private final CallGraphDependencyScope selection;

    /** Changed classes mapped to their path evidence. */
    private final Map<String, List<String>> changedClassPaths;

    /** Optional strategy-provided external methods that retain real IR. */
    private final Predicate<IMethod> retainedBody;

    /** Number of strategy-retained external types. */
    private final int retainedTypeCount;

    /** Stable dangerous-transfer set. */
    private final Set<DependencyBoundaryTransfer> dangerousTransfers =
            new LinkedHashSet<>();

    /** Stable factory evidence set. */
    private final Set<DependencyFactoryFinding> factories =
            new LinkedHashSet<>();

    /**
     * Creates a strategy-neutral boundary with no retained body exceptions.
     *
     * @param dependencyScope external method-body scope
     * @param changes graph-relevant changes
     * @param ownershipIndex classpath winner ownership
     * @param classHierarchy resolved class hierarchy
     */
    public DependencyBodyBoundary(
            final CallGraphDependencyScope dependencyScope,
            final List<CallGraphChange> changes,
            final ClassOwnershipIndex ownershipIndex,
            final IClassHierarchy classHierarchy) {
        this(dependencyScope, changes, ownershipIndex, classHierarchy,
                ignored -> false, 0);
    }

    /**
     * Creates a boundary with a strategy-provided retained-body policy.
     *
     * @param dependencyScope external method-body scope
     * @param changes graph-relevant changes
     * @param ownershipIndex classpath winner ownership
     * @param classHierarchy resolved class hierarchy
     * @param retainedMethodBody retained external method predicate
     * @param retainedExternalTypes retained external type count
     */
    public DependencyBodyBoundary(
            final CallGraphDependencyScope dependencyScope,
            final List<CallGraphChange> changes,
            final ClassOwnershipIndex ownershipIndex,
            final IClassHierarchy classHierarchy,
            final Predicate<IMethod> retainedMethodBody,
            final int retainedExternalTypes) {
        ownership = Objects.requireNonNull(ownershipIndex, "ownershipIndex");
        hierarchy = Objects.requireNonNull(classHierarchy, "classHierarchy");
        selection = Objects.requireNonNull(dependencyScope,
                "dependencyScope");
        retainedBody = Objects.requireNonNull(retainedMethodBody,
                "retainedMethodBody");
        if (retainedExternalTypes < 0) {
            throw new IllegalArgumentException(
                    "retainedExternalTypes must not be negative");
        }
        retainedTypeCount = retainedExternalTypes;
        changedClassPaths = changedClassPaths(changes);
    }

    private Map<String, List<String>> changedClassPaths(
            final List<CallGraphChange> changes) {
        final Map<ArtifactCoord, List<String>> artifactPaths =
                new LinkedHashMap<>();
        for (var path : selection.paths()) {
            artifactPaths.computeIfAbsent(path.seed(), ignored ->
                    new ArrayList<>()).add(path.stablePath());
        }
        final Map<String, List<String>> result = new LinkedHashMap<>();
        for (var point : Objects.requireNonNull(changes, "changes")) {
            final String owner = normalizeType(point.owner());
            final List<String> paths = artifactPaths.getOrDefault(
                    point.artifact(),
                    List.of());
            result.computeIfAbsent(owner, ignored -> new ArrayList<>())
                    .addAll(paths);
        }
        final Map<String, List<String>> immutable = new LinkedHashMap<>();
        result.forEach((key, value) -> immutable.put(key,
                value.stream().distinct().sorted().toList()));
        return Collections.unmodifiableMap(immutable);
    }

    private String normalizeType(final String owner) {
        final String slashed = owner.replace('.', '/');
        return slashed.startsWith("L") ? slashed : "L" + slashed;
    }

    /**
     * Installs the shared boundary into a propagation builder.
     *
     * @param builder algorithm builder
     */
    public void install(final SSAPropagationCallGraphBuilder builder) {
        if (!active()) {
            return;
        }
        final SSAContextInterpreter base =
                builder.getCFAContextInterpreter();
        builder.setContextSelector(wrapSelector(
                builder.getContextSelector(), base));
        builder.setContextInterpreter(wrapInterpreter(base));
    }

    /** @return whether changed-paths boundary interpretation is active */
    public boolean active() {
        return selection.mode()
                == DependencyScopeMode.CHANGED_PATHS;
    }

    /**
     * Wraps the propagation selector before builder construction.
     *
     * @param base existing selector
     * @param interpreter existing interpreter
     * @return boundary-aware selector
     */
    public ContextSelector wrapSelector(
            final ContextSelector base,
            final SSAContextInterpreter interpreter) {
        return new BoundaryContextSelector(base, interpreter);
    }

    /**
     * Wraps the propagation interpreter before builder construction.
     *
     * @param base existing interpreter
     * @return boundary-aware interpreter
     */
    public SSAContextInterpreter wrapInterpreter(
            final SSAContextInterpreter base) {
        return new BoundaryInterpreter(base);
    }

    /**
     * @param method resolved method
     * @return whether its body is no-op
     */
    public boolean noOp(final IMethod method) {
        final ClassOwnership owner = ownership.ownershipOf(
                method.getDeclaringClass().getName().toString());
        return owner != null && owner.getOrigin() == CodeOrigin.DEPENDENCY
                && !retainedBody.test(method)
                && owner.getSource().artifact().map(selection::policyFor)
                .orElse(DependencyBodyPolicy.REAL_IR)
                == DependencyBodyPolicy.NO_OP;
    }

    private ArtifactCoord artifact(final IMethod method) {
        final ClassOwnership owner = ownership.ownershipOf(
                method.getDeclaringClass().getName().toString());
        return owner == null ? null : owner.getSource().artifact()
                .orElse(null);
    }

    /**
     * Snapshots evidence and reachable method-node policy counts.
     *
     * @param graph completed Call Graph
     * @return immutable boundary metadata
     */
    public DependencyBodyBoundaryMetadata metadata(
            final com.ibm.wala.ipa.callgraph.CallGraph graph) {
        return metadata(graph, 0);
    }

    /**
     * Snapshots boundary metadata and external-target pruning counts.
     *
     * @param graph completed Call Graph
     * @param prunedExternalTargets pruned external targets
     * @return immutable boundary metadata
     */
    public DependencyBodyBoundaryMetadata metadata(
            final com.ibm.wala.ipa.callgraph.CallGraph graph,
            final int prunedExternalTargets) {
        int real = 0;
        int noOp = 0;
        int factory = 0;
        int retained = 0;
        final Set<String> noOpMethods = new LinkedHashSet<>();
        for (CGNode node : graph) {
            final ClassOwnership owner = ownership.ownershipOf(
                    node.getMethod().getDeclaringClass().getName().toString());
            if (owner == null || owner.getOrigin() != CodeOrigin.DEPENDENCY) {
                continue;
            }
            if (!noOp(node.getMethod())) {
                real++;
                if (retainedBody.test(node.getMethod())) {
                    retained++;
                }
            } else if (factoryFact(node.getContext()) != null) {
                factory++;
                noOpMethods.add(node.getMethod().getReference().toString());
            } else {
                noOp++;
                noOpMethods.add(node.getMethod().getReference().toString());
            }
        }
        return new DependencyBodyBoundaryMetadata(
                new ArrayList<>(dangerousTransfers),
                new ArrayList<>(factories), List.of(), noOpMethods,
                real, noOp, factory,
                retainedTypeCount, retained,
                prunedExternalTargets);
    }

    private FactoryFact factoryFact(final Context context) {
        final ContextItem value = context.get(FactoryContextKey.INSTANCE);
        return value instanceof FactoryFact ? (FactoryFact) value : null;
    }

    /** Adds caller-sensitive factory Context and records boundary evidence. */
    private final class BoundaryContextSelector implements ContextSelector {

        /** Previously installed algorithm selector. */
        private final ContextSelector base;

        /** Previously installed algorithm interpreter. */
        private final SSAContextInterpreter interpreter;

        BoundaryContextSelector(
                final ContextSelector delegate,
                final SSAContextInterpreter baseInterpreter) {
            base = Objects.requireNonNull(delegate, "delegate");
            interpreter = Objects.requireNonNull(
                    baseInterpreter, "baseInterpreter");
        }

        @Override
        public Context getCalleeTarget(
                final CGNode caller,
                final CallSiteReference site,
                final IMethod callee,
                final com.ibm.wala.ipa.callgraph.propagation.InstanceKey[]
                        actualParameters) {
            final Context delegated = base.getCalleeTarget(
                    caller, site, callee, actualParameters);
            if (!noOp(callee)) {
                return delegated;
            }
            final IR callerIr = caller.getIR() != null
                    ? caller.getIR()
                    : interpreter.understands(caller)
                    ? interpreter.getIR(caller) : null;
            final SSAAbstractInvokeInstruction invoke = invoke(
                    callerIr, site);
            if (invoke == null) {
                return delegated;
            }
            recordDangerousTransfers(caller, site, callee, callerIr,
                    invoke);
            final FactoryFact fact = factoryFact(caller, site, callee,
                    callerIr, invoke);
            if (fact == null) {
                return delegated;
            }
            final Context safe = delegated == null
                    ? com.ibm.wala.ipa.callgraph.impl.Everywhere.EVERYWHERE
                    : delegated;
            recordFactories(caller, callee, fact);
            return new FactoryContext(safe, fact);
        }

        @Override
        public IntSet getRelevantParameters(
                final CGNode caller,
                final CallSiteReference site) {
            return base.getRelevantParameters(caller, site);
        }
    }

    private SSAAbstractInvokeInstruction invoke(
            final IR ir, final CallSiteReference site) {
        if (ir == null) {
            return null;
        }
        for (SSAInstruction instruction : ir.getInstructions()) {
            if (instruction instanceof SSAAbstractInvokeInstruction) {
                final SSAAbstractInvokeInstruction candidate =
                        (SSAAbstractInvokeInstruction) instruction;
                if (candidate.getProgramCounter()
                        == site.getProgramCounter()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private FactoryFact factoryFact(
            final CGNode caller,
            final CallSiteReference site,
            final IMethod callee,
            final IR ir,
            final SSAAbstractInvokeInstruction invoke) {
        if (!callee.getReturnType().isReferenceType()
                || invoke.getNumberOfReturnValues() != 1) {
            return null;
        }
        final DefUse defUse = new DefUse(ir);
        final Map<TypeReference, Integer> inferred = new LinkedHashMap<>();
        final Deque<FlowValue> work = new ArrayDeque<>();
        work.add(new FlowValue(invoke.getReturnValue(0), 0));
        final Set<Integer> visited = new LinkedHashSet<>();
        while (!work.isEmpty()) {
            final FlowValue value = work.removeFirst();
            if (value.depth() > MAX_FLOW_HOPS
                    || !visited.add(value.valueNumber())) {
                continue;
            }
            final Iterator<SSAInstruction> uses = defUse.getUses(
                    value.valueNumber());
            while (uses.hasNext()) {
                final SSAInstruction use = uses.next();
                if (use instanceof SSACheckCastInstruction) {
                    final SSACheckCastInstruction cast =
                            (SSACheckCastInstruction) use;
                    for (TypeReference type : cast.getDeclaredResultTypes()) {
                        if (validFactoryType(type)) {
                            inferred.putIfAbsent(type, cast.iIndex());
                        }
                    }
                } else if (use instanceof SSAPhiInstruction
                        || use instanceof SSAPiInstruction) {
                    if (use.hasDef()) {
                        work.add(new FlowValue(use.getDef(),
                                value.depth() + 1));
                    }
                }
            }
        }
        if (inferred.isEmpty()) {
            return null;
        }
        final List<TypeReference> types = inferred.keySet().stream()
                .sorted(java.util.Comparator.comparing(TypeReference::toString))
                .toList();
        final List<Integer> casts = types.stream().map(inferred::get).toList();
        return new FactoryFact(types, casts,
                caller.getMethod().getReference().toString(),
                site.getProgramCounter());
    }

    /**
     * Local value and traversal depth.
     *
     * @param valueNumber SSA value number
     * @param depth bounded traversal depth
     */
    private record FlowValue(int valueNumber, int depth) {
    }

    private boolean validFactoryType(final TypeReference type) {
        final IClass klass = hierarchy.lookupClass(type);
        if (klass == null || klass.isInterface() || klass.isAbstract()) {
            return false;
        }
        final ClassOwnership owner = ownership.ownershipOf(
                klass.getName().toString());
        if (owner == null) {
            return false;
        }
        if (owner.getOrigin() == CodeOrigin.PROJECT
                || owner.getOrigin() == CodeOrigin.REACTOR_DEPENDENCY) {
            return true;
        }
        return owner.getOrigin() == CodeOrigin.DEPENDENCY
                && owner.getSource().artifact().map(selection::policyFor)
                .orElse(DependencyBodyPolicy.NO_OP)
                == DependencyBodyPolicy.REAL_IR;
    }

    private void recordFactories(
            final CGNode caller,
            final IMethod callee,
            final FactoryFact fact) {
        final ArtifactCoord calleeArtifact = artifact(callee);
        if (calleeArtifact == null) {
            return;
        }
        for (int index = 0; index < fact.types().size(); index++) {
            factories.add(new DependencyFactoryFinding(
                    caller.getMethod().getReference().toString(),
                    callee.getReference().toString(), calleeArtifact,
                    fact.bytecodePc(), fact.castInstructions().get(index),
                    fact.types().get(index).toString(),
                    caller.getContext().toString()));
        }
    }

    private void recordDangerousTransfers(
            final CGNode caller,
            final CallSiteReference site,
            final IMethod callee,
            final IR ir,
            final SSAAbstractInvokeInstruction invoke) {
        final ArtifactCoord calleeArtifact = artifact(callee);
        if (calleeArtifact == null || changedClassPaths.isEmpty()) {
            return;
        }
        TypeInference inference = null;
        try {
            inference = TypeInference.make(ir, false);
        } catch (RuntimeException ignored) {
            // Descriptor and bounded DefUse evidence remain available.
        }
        final DefUse defUse = new DefUse(ir);
        for (int index = 0; index < invoke.getNumberOfUses(); index++) {
            final TypeProof proof = changedType(
                    callee, index, invoke.getUse(index), inference,
                    defUse, 0, new LinkedHashSet<>());
            if (proof == null) {
                continue;
            }
            final int argument = !invoke.isStatic() && index == 0
                    ? -1 : index - (invoke.isStatic() ? 0 : 1);
            dangerousTransfers.add(new DependencyBoundaryTransfer(
                    caller.getMethod().getReference().toString(),
                    originOf(caller.getMethod().getDeclaringClass()),
                    site.getProgramCounter(),
                    site.getInvocationCode().toString(),
                    callee.getReference().toString(), calleeArtifact,
                    argument, proof.changedClass(), proof.evidence(),
                    changedClassPaths.getOrDefault(
                            proof.changedClass(), List.of()),
                    caller.getContext().toString()));
        }
    }

    private TypeProof changedType(
            final IMethod callee,
            final int useIndex,
            final int value,
            final TypeInference inference,
            final DefUse defUse,
            final int depth,
            final Set<Integer> visited) {
        if (depth > MAX_FLOW_HOPS || !visited.add(value)) {
            return null;
        }
        if (useIndex < callee.getNumberOfParameters()) {
            final TypeProof descriptor = proof(
                    callee.getParameterType(useIndex), "DESCRIPTOR");
            if (descriptor != null) {
                return descriptor;
            }
        }
        if (inference != null) {
            try {
                final TypeAbstraction inferred = inference.getType(value);
                if (inferred != null) {
                    final TypeProof proof = proof(
                            inferred.getTypeReference(), "TYPE_INFERENCE");
                    if (proof != null) {
                        return proof;
                    }
                }
            } catch (RuntimeException ignored) {
                // Continue with bounded definition recovery.
            }
        }
        final SSAInstruction definition = defUse.getDef(value);
        if (definition instanceof SSACheckCastInstruction) {
            final SSACheckCastInstruction cast =
                    (SSACheckCastInstruction) definition;
            for (TypeReference type : cast.getDeclaredResultTypes()) {
                final TypeProof proof = proof(type, "DEF_USE_CHECKCAST");
                if (proof != null) {
                    return proof;
                }
            }
            return changedType(callee, useIndex, cast.getVal(), inference,
                    defUse, depth + 1, visited);
        }
        if (definition instanceof SSANewInstruction) {
            return proof(((SSANewInstruction) definition).getConcreteType(),
                    "DEF_USE_NEW");
        }
        if (definition instanceof SSAPhiInstruction
                || definition instanceof SSAPiInstruction) {
            for (int index = 0; index < definition.getNumberOfUses(); index++) {
                final TypeProof proof = changedType(callee, useIndex,
                        definition.getUse(index), inference, defUse,
                        depth + 1, visited);
                if (proof != null) {
                    return proof;
                }
            }
        }
        return null;
    }

    private TypeProof proof(
            final TypeReference type, final String evidence) {
        if (type == null) {
            return null;
        }
        final String normalized = type.isArrayType()
                ? type.getArrayElementType().getName().toString()
                : type.getName().toString();
        return changedClassPaths.containsKey(normalized)
                ? new TypeProof(normalized, type.isArrayType()
                ? evidence + "_ARRAY_ELEMENT" : evidence) : null;
    }

    /**
     * Recovered changed type plus proof kind.
     *
     * @param changedClass changed class type
     * @param evidence proof kind
     */
    private record TypeProof(String changedClass, String evidence) {
    }

    private CodeOrigin originOf(final IClass type) {
        final ClassOwnership owner = ownership.ownershipOf(
                type.getName().toString());
        if (owner != null) {
            return owner.getOrigin();
        }
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader)
                ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
    }

    /** Executes factory or typed no-op IR for no-op dependency nodes. */
    private final class BoundaryInterpreter implements SSAContextInterpreter {

        /** Previously installed interpreter. */
        private final SSAContextInterpreter base;

        /** Boundary IR cache. */
        private final Map<CGNode, IR> irs = new HashMap<>();

        BoundaryInterpreter(final SSAContextInterpreter delegate) {
            base = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean understands(final CGNode node) {
            return noOp(node.getMethod());
        }

        @Override
        public IR getIR(final CGNode node) {
            return understands(node)
                    ? irs.computeIfAbsent(node, this::summary)
                    : base.getIR(node);
        }

        private IR summary(final CGNode node) {
            final IMethod method = node.getMethod();
            final MethodSummary summary = new MethodSummary(
                    method.getReference());
            summary.setStatic(method.isStatic());
            final SSAInstructionFactory instructions = method
                    .getDeclaringClass().getClassLoader().getLanguage()
                    .instructionFactory();
            final FactoryFact factory = factoryFact(node.getContext());
            if (factory != null) {
                summary.setFactory(true);
                int instruction = 0;
                int value = method.getNumberOfParameters() + 2;
                final int[] allocations = new int[factory.types().size()];
                for (int index = 0; index < factory.types().size(); index++) {
                    allocations[index] = value++;
                    summary.addStatement(instructions.NewInstruction(
                            instruction, allocations[index],
                            NewSiteReference.make(instruction,
                                    factory.types().get(index))));
                    instruction++;
                }
                final int returned;
                if (allocations.length == 1) {
                    returned = allocations[0];
                } else {
                    returned = value;
                    summary.addStatement(instructions.PhiInstruction(
                            instruction++, returned, allocations));
                }
                summary.addStatement(instructions.ReturnInstruction(
                        instruction, returned, false));
            } else {
                noOpReturn(method, summary, instructions);
            }
            return new SummarizedMethod(method.getReference(), summary,
                    method.getDeclaringClass()).makeIR(
                    node.getContext(), SSAOptions.defaultOptions());
        }

        private void noOpReturn(
                final IMethod method,
                final MethodSummary summary,
                final SSAInstructionFactory instructions) {
            final TypeReference type = method.getReturnType();
            if (TypeReference.Void.equals(type)) {
                summary.addStatement(instructions.ReturnInstruction(0));
                return;
            }
            final int value = method.getNumberOfParameters() + 2;
            summary.addConstant(value, defaultValue(type));
            summary.addStatement(instructions.ReturnInstruction(
                    0, value, type.isPrimitiveType()));
        }

        private ConstantValue defaultValue(final TypeReference type) {
            if (!type.isPrimitiveType()) {
                return new ConstantValue(null);
            }
            if (TypeReference.Long.equals(type)) {
                return new ConstantValue(0L);
            }
            if (TypeReference.Float.equals(type)) {
                return new ConstantValue(0.0F);
            }
            if (TypeReference.Double.equals(type)) {
                return new ConstantValue(0.0D);
            }
            return new ConstantValue(0);
        }

        @Override
        public Iterator<NewSiteReference> iterateNewSites(
                final CGNode node) {
            return getIR(node).iterateNewSites();
        }

        @Override
        public Iterator<FieldReference> iterateFieldsRead(
                final CGNode node) {
            return understands(node) ? Collections.emptyIterator()
                    : base.iterateFieldsRead(node);
        }

        @Override
        public Iterator<FieldReference> iterateFieldsWritten(
                final CGNode node) {
            return understands(node) ? Collections.emptyIterator()
                    : base.iterateFieldsWritten(node);
        }

        @Override
        public boolean recordFactoryType(
                final CGNode node, final IClass klass) {
            return understands(node) && factoryFact(node.getContext()) != null
                    || base.recordFactoryType(node, klass);
        }

        @Override
        public Iterator<CallSiteReference> iterateCallSites(
                final CGNode node) {
            return understands(node) ? Collections.emptyIterator()
                    : base.iterateCallSites(node);
        }

        @Override
        public IRView getIRView(final CGNode node) {
            return getIR(node);
        }

        @Override
        public DefUse getDU(final CGNode node) {
            return understands(node) ? new DefUse(getIR(node))
                    : base.getDU(node);
        }

        @Override
        public int getNumberOfStatements(final CGNode node) {
            return understands(node) ? getIR(node).getInstructions().length
                    : base.getNumberOfStatements(node);
        }

        @Override
        public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(
                final CGNode node) {
            return understands(node) ? getIR(node).getControlFlowGraph()
                    : base.getCFG(node);
        }
    }
}
