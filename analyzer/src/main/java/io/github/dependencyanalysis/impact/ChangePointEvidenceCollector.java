package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
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
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.ServiceProviderRegistration;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyCapabilities;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidence;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.local.LocalConstantResolution;
import io.github.dependencyanalysis.callgraph.local.LocalConstantResolver;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

// Wiki: wiki/features/impact-tracing.md - Unified Evidence Collection
/** Collects and binds all reachable terminal references after graph build. */
public final class ChangePointEvidenceCollector {

    /** Exact supported Class.forName overload. */
    private static final String CLASS_FOR_NAME_DESCRIPTOR =
            "(Ljava/lang/String;)Ljava/lang/Class;";

    /** Minimum interval between same-thread TRACE progress events. */
    private static final long TRACE_INTERVAL_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(5L);

    /** Scanned graph node counter index. */
    private static final int SCANNED_NODES = 0;

    /** Scanned method body counter index. */
    private static final int SCANNED_BODIES = 1;

    /** Scanned SSA instruction counter index. */
    private static final int SCANNED_INSTRUCTIONS = 2;

    /** Collected Evidence counter index. */
    private static final int COLLECTED_EVIDENCE = 3;

    /** Unified binding counter index. */
    private static final int COLLECTED_BINDINGS = 4;

    /** Total scan counter array size. */
    private static final int SCAN_COUNTER_SIZE = 5;

    /** Bounded caller-local constant resolver. */
    private final LocalConstantResolver constants =
            new LocalConstantResolver();

    /** Optional production diagnostics; absent for direct library callers. */
    private final DiagnosticLog diagnostics;

    /** Creates a collector without progress diagnostics. */
    public ChangePointEvidenceCollector() {
        diagnostics = null;
    }

    /**
     * Creates a collector with same-thread TRACE progress.
     *
     * @param collector diagnostics
     */
    public ChangePointEvidenceCollector(final DiagnosticLog collector) {
        diagnostics = java.util.Objects.requireNonNull(
                collector, "collector");
    }

    /**
     * Collects a complete immutable index for one module.
     *
     * @param unit module analysis input
     * @param graph completed target Call Graph
     * @param ownership target ownership index
     * @param dynamicEvidence strategy dynamic observations
     * @param structuralReferences raw structural metadata facts
     * @param capabilities effective graph strategy capabilities
     * @param bodyAvailable whether the strategy traversed one method body
     * @return complete immutable evidence index
     */
    public ChangePointEvidenceIndex collect(
            final ModuleAnalysisUnit unit,
            final CallGraph graph,
            final ClassOwnershipIndex ownership,
            final DynamicCallEvidenceIndex dynamicEvidence,
            final StructuralReferenceIndex structuralReferences,
            final CallGraphStrategyCapabilities capabilities,
            final Predicate<IMethod> bodyAvailable) {
        final Map<BoundChangePoint, List<ReferenceEvidence>> evidence =
                new LinkedHashMap<>();
        unit.getChangePoints().forEach(point -> evidence.put(
                point, new ArrayList<>()));
        final Map<String, List<BoundChangePoint>> byOwner = byOwner(unit);
        final ChangeMatcherIndex matcher = new ChangeMatcherIndex(unit);
        final Set<ModelLimitation> limitations = new LinkedHashSet<>();
        final Map<QueryNode, Set<ChangePointTerminal>> bindings =
                new LinkedHashMap<>();
        final int[] localCounts = new int[2];
        final long[] scanCounts = new long[SCAN_COUNTER_SIZE];
        final long[] traceState = new long[]{System.nanoTime()};
        final DiagnosticContext diagnosticContext = DiagnosticContext.of(
                "module-analysis", "evidence-analysis").withModule(
                unit.getModuleId().stableKey());
        final CollectionContext context = new CollectionContext(
                unit, ownership, byOwner, matcher, evidence, bindings,
                limitations, capabilities, localCounts, scanCounts,
                traceState, diagnosticContext);
        collectResourceAnchors(unit, evidence);
        unit.getServiceLoaderResourceIssues().forEach(issue ->
                limitations.add(serviceLoaderLimitation(
                        issue.code(), issue.location(), issue.detail())));
        final StructuralBindings structural = collectStructural(
                context, structuralReferences);
        for (CGNode node : graph) {
            if (node.equals(graph.getFakeRootNode())
                    || node.equals(graph.getFakeWorldClinitNode())) {
                continue;
            }
            scanCounts[SCANNED_NODES]++;
            bindStructural(context, node, structural);
            collectMethodDeclaration(context, node);
            if (!bodyAvailable.test(node.getMethod())) {
                traceProgress(context);
                continue;
            }
            final IR ir = node.getIR();
            if (ir == null) {
                traceProgress(context);
                continue;
            }
            scanCounts[SCANNED_BODIES]++;
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction == null) {
                    continue;
                }
                scanCounts[SCANNED_INSTRUCTIONS]++;
                collectInstruction(context, node, instruction);
            }
            traceProgress(context);
        }
        collectDynamic(context, dynamicEvidence);
        final List<ChangePointEvidenceResolution> resolutions =
                new ArrayList<>();
        for (BoundChangePoint point : unit.getChangePoints().stream()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList()) {
            final List<ReferenceEvidence> values = evidence.get(point);
            final EvidenceResolutionStatus status = isAdded(
                    point.getChangePoint().getKind())
                    ? EvidenceResolutionStatus.UNSUPPORTED
                    : values.isEmpty() ? EvidenceResolutionStatus.NONE
                    : EvidenceResolutionStatus.MATCHED;
            resolutions.add(new ChangePointEvidenceResolution(
                    point, status, values, List.of()));
        }
        final Map<QueryNode, List<ChangePointTerminal>> frozenBindings =
                new LinkedHashMap<>();
        bindings.forEach((node, terminals) -> frozenBindings.put(
                node, List.copyOf(terminals)));
        return new ChangePointEvidenceIndex(resolutions, frozenBindings,
                limitations.stream().sorted()
                        .map(new CallGraphCoverageMapper()::model).toList(),
                localCounts[0], localCounts[1]);
    }

    private void collectResourceAnchors(
            final ModuleAnalysisUnit unit,
            final Map<BoundChangePoint, List<ReferenceEvidence>> result) {
        for (BoundChangePoint point : unit.getChangePoints()) {
            final ServiceProviderRegistration registration = point
                    .getChangePoint().getServiceRegistration().orElse(null);
            if (registration == null) {
                continue;
            }
            result.get(point).add(new ReferenceEvidence(Optional.of(
                    new ResourceEvidenceAnchor(registration.resourcePath(),
                            registration.serviceInternalName(),
                            registration.providerInternalName())),
                    new ReferenceTarget(registration.serviceInternalName(),
                            registration.providerInternalName(),
                            registration.resourcePath()),
                    EvidenceKind.RESOURCE_REFERENCE,
                    EvidenceMechanism.SERVICE_LOADER_PROVIDER,
                    new EvidenceLocation(registration.resourcePath(), -1),
                    "baseline ServiceLoader registration removed"));
        }
    }

    private StructuralBindings collectStructural(
            final CollectionContext context,
            final StructuralReferenceIndex structuralReferences) {
        final Map<String, List<ChangePointTerminal>> byMethod =
                new LinkedHashMap<>();
        final Map<String, List<ChangePointTerminal>> byStructuralOwner =
                new LinkedHashMap<>();
        for (StructuralReference reference : structuralReferences
                .references()) {
            for (BoundChangePoint point : context.byOwner().getOrDefault(
                    reference.getChangedClass(), List.of())) {
                final ChangePointKind kind = point.getChangePoint().getKind();
                if (kind != ChangePointKind.CLASS_REMOVED
                        && kind != ChangePointKind.CLASS_ACCESS_NARROWED) {
                    continue;
                }
                final ReferenceEvidence value = new ReferenceEvidence(
                        Optional.of(new StructuralEvidenceAnchor(reference)),
                        new ReferenceTarget(reference.getChangedClass(),
                                reference.getReferencingMember(), ""),
                        EvidenceKind.STRUCTURAL_REFERENCE,
                        EvidenceMechanism.STRUCTURAL_METADATA,
                        new EvidenceLocation(
                                reference.getReferencingClass(), -1),
                        reference.getEvidence());
                addEvidence(context, point, value);
                if (reference.getOrigin() == CodeOrigin.PROJECT) {
                    continue;
                }
                final ChangePointTerminal terminal =
                        new ChangePointTerminal(point, value);
                final String member = reference.getReferencingMember();
                if (member.indexOf('(') >= 0) {
                    byMethod.computeIfAbsent(structuralMethodKey(
                            reference.getReferencingClass(), member),
                            ignored -> new ArrayList<>()).add(terminal);
                } else {
                    byStructuralOwner.computeIfAbsent(
                            reference.getReferencingClass(),
                            ignored -> new ArrayList<>()).add(terminal);
                }
            }
        }
        return new StructuralBindings(byMethod, byStructuralOwner);
    }

    private void bindStructural(
            final CollectionContext context,
            final CGNode node,
            final StructuralBindings structural) {
        final MethodReference method = node.getMethod().getReference();
        final String methodOwner = owner(method);
        final List<ChangePointTerminal> ownerBindings = structural.byOwner()
                .getOrDefault(methodOwner, List.of());
        final List<ChangePointTerminal> methodBindings = structural.byMethod()
                .getOrDefault(structuralMethodKey(methodOwner,
                        method.getName() + method.getDescriptor().toString()),
                        List.of());
        if (ownerBindings.isEmpty() && methodBindings.isEmpty()) {
            return;
        }
        final QueryNode queryNode = queryNode(context, node);
        ownerBindings.forEach(terminal -> addBinding(
                context, queryNode, terminal));
        methodBindings.forEach(terminal -> addBinding(
                context, queryNode, terminal));
    }

    private String structuralMethodKey(
            final String owner,
            final String member) {
        return owner + "#" + member;
    }

    private Map<String, List<BoundChangePoint>> byOwner(
            final ModuleAnalysisUnit unit) {
        final Map<String, List<BoundChangePoint>> result =
                new LinkedHashMap<>();
        for (BoundChangePoint point : unit.getChangePoints()) {
            result.computeIfAbsent(point.getChangePoint().getOwner(),
                    ignored -> new ArrayList<>()).add(point);
        }
        return result;
    }

    private void collectMethodDeclaration(
            final CollectionContext context,
            final CGNode node) {
        for (BoundChangePoint point : context.matcher()
                .methodDeclarations(node.getMethod().getReference())) {
            final ChangePoint change = point.getChangePoint();
            if (change.getKind() == ChangePointKind.METHOD_BODY_CHANGED) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.empty(),
                        context.ownership(),
                        new ReferenceTarget(change.getOwner(),
                                change.getName(),
                                change.getNewDescriptor()),
                        EvidenceKind.METHOD_REFERENCE,
                        EvidenceMechanism.METHOD_DECLARATION, -1,
                        "reachable changed method declaration")));
            }
        }
    }

    private void collectInstruction(
            final CollectionContext context,
            final CGNode node,
            final SSAInstruction instruction) {
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            collectInvoke(context, node, invoke);
        }
        if (instruction instanceof SSAFieldAccessInstruction field) {
            collectField(context, node, field);
        }
        for (String type : referencedTypes(instruction)) {
            for (BoundChangePoint point : context.matcher()
                    .classReferences(type)) {
                final ChangePointKind kind = point.getChangePoint().getKind();
                if (kind == ChangePointKind.CLASS_REMOVED
                        || kind == ChangePointKind.CLASS_ACCESS_NARROWED) {
                    addEvidence(context, point, evidence(new EvidenceInput(
                            context.unit(), node, Optional.of(instruction),
                            context.ownership(),
                            new ReferenceTarget(type, "", ""),
                            EvidenceKind.TYPE_REFERENCE,
                            EvidenceMechanism.BYTECODE_TYPE_REFERENCE,
                            pc(instruction), "reachable bytecode type")));
                }
            }
        }
    }

    private void collectInvoke(
            final CollectionContext context,
        final CGNode node,
            final SSAAbstractInvokeInstruction invoke) {
        final MethodReference target = invoke.getDeclaredTarget();
        for (BoundChangePoint point : context.matcher()
                .methodReferences(target)) {
            final ChangePoint change = point.getChangePoint();
            if (change.getKind() == ChangePointKind.METHOD_REMOVED
                    || change.getKind()
                    == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                    || change.getKind()
                    == ChangePointKind.METHOD_ACCESS_NARROWED) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.of(invoke),
                        context.ownership(),
                        new ReferenceTarget(change.getOwner(),
                                change.getName(), target.getDescriptor()
                                .toString()),
                        EvidenceKind.METHOD_REFERENCE,
                        EvidenceMechanism.DECLARED_INVOKE,
                        invoke.getProgramCounter(), target.toString())));
            }
        }
        if (jdkCaller(node, context.ownership())) {
            return;
        }
        if (!classForName(target)) {
            if (serviceLoaderLoad(target)) {
                collectServiceLoader(context, node, invoke);
            }
            return;
        }
        final String location = node.getMethod().getReference() + "|pc="
                + invoke.getProgramCounter();
        if (invoke.getNumberOfUses() == 0) {
            context.localCounts()[1]++;
            if (localReflection(context.capabilities())) {
                context.limitations().add(reflectionLimitation(
                        location, "invoke-argument-unavailable"));
            }
            return;
        }
        final LocalConstantResolution constant = constants.resolve(
                node.getIR(), invoke.getUse(0),
                LocalConstantResolver.ConstantKind.STRING);
        if (constant.status() != LocalConstantResolution.Status.RESOLVED) {
            context.localCounts()[1]++;
            if (localReflection(context.capabilities())) {
                context.limitations().add(reflectionLimitation(
                        location, constant.detail()));
            }
            return;
        }
        context.localCounts()[0]++;
        final String owner = className(constant.stringValue().orElseThrow());
        if (owner == null) {
                context.limitations().add(new ModelLimitation(
                    ModelKind.REFLECTION,
                    "CLASS_FOR_NAME_LITERAL_INVALID",
                    location, invalidClassNameDetail(
                            constant.stringValue().orElseThrow())));
            return;
        }
        for (BoundChangePoint point : context.byOwner().getOrDefault(
                owner, List.of())) {
            if (point.getChangePoint().getKind()
                    == ChangePointKind.CLASS_REMOVED) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.of(invoke),
                        context.ownership(),
                        new ReferenceTarget(owner, "", ""),
                        EvidenceKind.TYPE_REFERENCE,
                        EvidenceMechanism.CLASS_FOR_NAME_LOCAL_CONSTANT,
                        invoke.getProgramCounter(),
                        "Class.forName local constant="
                                + constant.stringValue().orElseThrow())));
            }
        }
    }

    private void collectServiceLoader(
            final CollectionContext context,
            final CGNode node,
            final SSAAbstractInvokeInstruction invoke) {
        final String location = node.getMethod().getReference() + "|pc="
                + invoke.getProgramCounter();
        if (invoke.getNumberOfUses() == 0) {
            context.localCounts()[1]++;
            if (localServiceLoader(context.capabilities())) {
                context.limitations().add(serviceLoaderLimitation(
                        "SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED",
                        location, "invoke-argument-unavailable"));
            }
            return;
        }
        final LocalConstantResolution constant = constants.resolve(
                node.getIR(), invoke.getUse(0),
                LocalConstantResolver.ConstantKind.CLASS);
        if (constant.status() != LocalConstantResolution.Status.RESOLVED) {
            context.localCounts()[1]++;
            if (localServiceLoader(context.capabilities())) {
                context.limitations().add(serviceLoaderLimitation(
                        "SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED",
                        location, constant.detail()));
            }
            return;
        }
        context.localCounts()[0]++;
        final String service = owner(constant.classValue().orElseThrow());
        for (BoundChangePoint point : context.unit().getChangePoints()) {
            final ChangePoint change = point.getChangePoint();
            if (change.getKind() == ChangePointKind.CLASS_REMOVED
                    && hasBaselineProvider(context.unit(), service,
                    change.getOwner())) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.of(invoke),
                        context.ownership(),
                        new ReferenceTarget(change.getOwner(), "", ""),
                        EvidenceKind.TYPE_REFERENCE,
                        EvidenceMechanism.SERVICE_LOADER_PROVIDER,
                        invoke.getProgramCounter(),
                        "ServiceLoader baseline provider; service="
                                + service + "; provider="
                                + change.getOwner())));
            }
            final ServiceProviderRegistration registration = change
                    .getServiceRegistration().orElse(null);
            if (registration != null && service.equals(
                    registration.serviceInternalName())) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.of(invoke),
                        context.ownership(),
                        new ReferenceTarget(service,
                                registration.providerInternalName(),
                                registration.resourcePath()),
                        EvidenceKind.RESOURCE_REFERENCE,
                        EvidenceMechanism.SERVICE_LOADER_PROVIDER,
                        invoke.getProgramCounter(),
                        "ServiceLoader registration removed; service="
                                + service + "; provider="
                                + registration.providerInternalName())));
            }
        }
    }

    private boolean hasBaselineProvider(
            final ModuleAnalysisUnit unit,
            final String service,
            final String provider) {
        return unit.getBaselineServiceRegistrations().stream()
                .anyMatch(value -> service.equals(
                        value.serviceInternalName())
                        && provider.equals(value.providerInternalName()));
    }

    private void collectField(
            final CollectionContext context,
            final CGNode node,
            final SSAFieldAccessInstruction field) {
        final FieldReference target = field.getDeclaredField();
        for (BoundChangePoint point : context.matcher()
                .fieldReferences(target)) {
            final ChangePoint change = point.getChangePoint();
            if ((change.getKind() == ChangePointKind.FIELD_REMOVED
                    || change.getKind()
                    == ChangePointKind.FIELD_DESCRIPTOR_CHANGED
                    || change.getKind()
                    == ChangePointKind.FIELD_ACCESS_NARROWED)
                    && matchesField(target, change)) {
                addEvidence(context, point, evidence(new EvidenceInput(
                        context.unit(), node, Optional.of(field),
                        context.ownership(),
                        new ReferenceTarget(change.getOwner(),
                                change.getName(), descriptor(
                                target.getFieldType())),
                        EvidenceKind.FIELD_REFERENCE,
                        EvidenceMechanism.BYTECODE_FIELD_REFERENCE,
                        pc(field), target.toString())));
            }
        }
    }

    private void collectDynamic(
            final CollectionContext context,
            final DynamicCallEvidenceIndex dynamic) {
        for (DynamicCallEvidence value : dynamic.all()) {
            for (BoundChangePoint point : context.unit().getChangePoints()) {
                final ChangePoint change = point.getChangePoint();
                if ((change.getKind() == ChangePointKind.METHOD_REMOVED
                        || change.getKind()
                        == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                        || change.getKind()
                        == ChangePointKind.METHOD_ACCESS_NARROWED)
                        && matches(value, change)) {
                    addEvidence(context, point, evidence(new EvidenceInput(
                            context.unit(), value.caller(), Optional.empty(),
                            context.ownership(),
                            new ReferenceTarget(value.targetOwner(),
                                    value.targetName(),
                                    value.targetDescriptor()),
                            EvidenceKind.METHOD_REFERENCE,
                            dynamicMechanism(value), value.bytecodePc(),
                            value.detail())));
                }
                if (change.getKind()
                        == ChangePointKind.CLASS_ACCESS_NARROWED
                        && change.getOwner().equals(value.targetOwner())) {
                    addEvidence(context, point, evidence(new EvidenceInput(
                            context.unit(), value.caller(), Optional.empty(),
                            context.ownership(),
                            new ReferenceTarget(value.targetOwner(), "", ""),
                            EvidenceKind.TYPE_REFERENCE,
                            dynamicMechanism(value), value.bytecodePc(),
                            value.detail())));
                }
                if (change.getKind()
                        == ChangePointKind.FIELD_ACCESS_NARROWED
                        && value.methodHandleKind().map(kind ->
                        kind.isField()).orElse(false)
                        && change.getOwner().equals(value.targetOwner())
                        && java.util.Objects.equals(change.getName(),
                        value.targetName())
                        && java.util.Objects.equals(
                        change.getNewDescriptor(),
                        value.targetDescriptor())) {
                    addEvidence(context, point, evidence(new EvidenceInput(
                            context.unit(), value.caller(), Optional.empty(),
                            context.ownership(),
                            new ReferenceTarget(value.targetOwner(),
                                    value.targetName(),
                                    value.targetDescriptor()),
                            EvidenceKind.FIELD_REFERENCE,
                            dynamicMechanism(value), value.bytecodePc(),
                            value.detail())));
                }
            }
        }
    }

    private ReferenceEvidence evidence(final EvidenceInput input) {
        final ModuleAnalysisUnit unit = input.unit();
        final CGNode node = input.node();
        final ClassOwnershipIndex ownership = input.ownership();
        final IMethod method = node.getMethod();
        final String owner = owner(method.getReference());
        final ClassOwnership source = ownership.ownershipOf(owner);
        final CodeOrigin origin = source == null
                ? CodeOrigin.SYNTHETIC : source.getOrigin();
        final String module = origin == CodeOrigin.PROJECT
                ? unit.getModuleId().stableKey() : origin.name();
        final MethodId methodId = new MethodId(owner,
                method.getName().toString(),
                method.getDescriptor().toString(), module,
                source == null ? "<synthetic>"
                        : source.getSource().toString());
        return new ReferenceEvidence(Optional.of(
                new MethodEvidenceAnchor(node, methodId, origin,
                        input.instruction())), input.target(), input.kind(),
                input.mechanism(), new EvidenceLocation(
                method.getReference().toString(), input.pc()),
                input.detail());
    }

    private void addEvidence(
            final CollectionContext context,
            final BoundChangePoint point,
            final ReferenceEvidence value) {
        context.result().get(point).add(value);
        context.scanCounts()[COLLECTED_EVIDENCE]++;
        final EvidenceAnchor anchor = value.anchor().orElse(null);
        if (anchor instanceof MethodEvidenceAnchor method) {
            addBinding(context, new WalaQueryNode(
                    method.node(), method.methodId(), method.origin()),
                    new ChangePointTerminal(point, value));
        }
    }

    private void addBinding(
            final CollectionContext context,
            final QueryNode node,
            final ChangePointTerminal terminal) {
        if (context.bindings().computeIfAbsent(node,
                ignored -> new LinkedHashSet<>()).add(terminal)) {
            context.scanCounts()[COLLECTED_BINDINGS]++;
        }
    }

    private QueryNode queryNode(
            final CollectionContext context,
            final CGNode node) {
        final IMethod method = node.getMethod();
        final String methodOwner = owner(method.getReference());
        final ClassOwnership source = context.ownership().ownershipOf(
                methodOwner);
        final CodeOrigin origin = source == null
                ? CodeOrigin.SYNTHETIC : source.getOrigin();
        final String module = origin == CodeOrigin.PROJECT
                ? context.unit().getModuleId().stableKey() : origin.name();
        return new WalaQueryNode(node, new MethodId(methodOwner,
                method.getName().toString(),
                method.getDescriptor().toString(), module,
                source == null ? "<synthetic>"
                        : source.getSource().toString()), origin);
    }

    private void traceProgress(final CollectionContext context) {
        if (diagnostics == null || !diagnostics.getVerbosity()
                .includes(LogVerbosity.TRACE)) {
            return;
        }
        final long now = System.nanoTime();
        if (now - context.traceState()[0] < TRACE_INTERVAL_NANOS) {
            return;
        }
        context.traceState()[0] = now;
        diagnostics.trace(context.diagnosticContext(),
                "Evidence progress; scannedNodes="
                        + context.scanCounts()[SCANNED_NODES]
                        + "; scannedBodies="
                        + context.scanCounts()[SCANNED_BODIES]
                        + "; scannedInstructions="
                        + context.scanCounts()[SCANNED_INSTRUCTIONS]
                        + "; evidence="
                        + context.scanCounts()[COLLECTED_EVIDENCE]
                        + "; bindings="
                        + context.scanCounts()[COLLECTED_BINDINGS]);
    }

    private ModelLimitation reflectionLimitation(
            final String location,
            final String detail) {
        return new ModelLimitation(ModelKind.REFLECTION,
                "CLASS_FOR_NAME_LOCAL_CONSTANT_UNRESOLVED",
                location, detail);
    }

    private ModelLimitation serviceLoaderLimitation(
            final String code,
            final String location,
            final String detail) {
        return new ModelLimitation(ModelKind.SERVICE_LOADER, code,
                location, detail);
    }

    private boolean localReflection(
            final CallGraphStrategyCapabilities capabilities) {
        return capabilities.reflection()
                == CallGraphStrategyCapabilities.SupportLevel.LOCAL;
    }

    private boolean localServiceLoader(
            final CallGraphStrategyCapabilities capabilities) {
        return capabilities.serviceLoader()
                == CallGraphStrategyCapabilities.SupportLevel.LOCAL;
    }

    private boolean jdkCaller(
            final CGNode node,
            final ClassOwnershipIndex ownership) {
        final ClassOwnership indexed = ownership.ownershipOf(
                node.getMethod().getDeclaringClass().getName().toString());
        if (indexed != null) {
            return indexed.getOrigin() == CodeOrigin.JDK;
        }
        final ClassLoaderReference loader = node.getMethod()
                .getDeclaringClass().getClassLoader().getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader);
    }

    /**
     * Immutable shared state for one completed-graph evidence scan.
     *
     * @param unit module input
     * @param ownership target ownership
     * @param byOwner bound changes grouped by owner
     * @param matcher ChangePoint matcher index
     * @param result mutable evidence accumulator
     * @param bindings exact QueryNode binding accumulator
     * @param limitations mutable limitation accumulator
     * @param capabilities effective strategy capabilities
     * @param localCounts success/unresolved caller-local counts
     * @param scanCounts node/body/instruction/evidence/binding counts
     * @param traceState last progress event monotonic time
     * @param diagnosticContext module Evidence diagnostic context
     */
    private record CollectionContext(
            ModuleAnalysisUnit unit,
            ClassOwnershipIndex ownership,
            Map<String, List<BoundChangePoint>> byOwner,
            ChangeMatcherIndex matcher,
            Map<BoundChangePoint, List<ReferenceEvidence>> result,
            Map<QueryNode, Set<ChangePointTerminal>> bindings,
            Set<ModelLimitation> limitations,
            CallGraphStrategyCapabilities capabilities,
            int[] localCounts,
            long[] scanCounts,
            long[] traceState,
            DiagnosticContext diagnosticContext) {
    }

    /**
     * Phase-local Structural Reference to reachable method lookup.
     *
     * @param byMethod exact referencing method lookup
     * @param byOwner class-level or field-level referencing owner lookup
     */
    private record StructuralBindings(
            Map<String, List<ChangePointTerminal>> byMethod,
            Map<String, List<ChangePointTerminal>> byOwner) {
    }

    private EvidenceMechanism dynamicMechanism(
            final DynamicCallEvidence evidence) {
        return switch (evidence.referenceKind()) {
            case BOOTSTRAP_IMPLEMENTATION_METHOD ->
                    EvidenceMechanism.INVOKEDYNAMIC_BOOTSTRAP;
            case BOOTSTRAP_ARGUMENT_METHOD_HANDLE ->
                    EvidenceMechanism.INVOKEDYNAMIC_HANDLE;
            case DIRECT_MODELED_HANDLE_TARGET ->
                    EvidenceMechanism.METHOD_HANDLE_TARGET;
        };
    }

    private boolean classForName(final MethodReference method) {
        return "java/lang/Class".equals(owner(method))
                && "forName".equals(method.getName().toString())
                && CLASS_FOR_NAME_DESCRIPTOR.equals(
                method.getDescriptor().toString());
    }

    private boolean serviceLoaderLoad(final MethodReference method) {
        return "java/util/ServiceLoader".equals(owner(method))
                && "load".equals(method.getName().toString())
                && "(Ljava/lang/Class;)Ljava/util/ServiceLoader;".equals(
                method.getDescriptor().toString());
    }

    private String className(final String value) {
        if (value.isBlank() || value.startsWith("[")
                || value.indexOf('/') >= 0 || value.indexOf(';') >= 0) {
            return null;
        }
        final String result = value.replace('.', '/');
        for (String part : result.split("/")) {
            if (part.isEmpty()
                    || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return null;
            }
            for (int index = 1; index < part.length(); index++) {
                if (!Character.isJavaIdentifierPart(part.charAt(index))) {
                    return null;
                }
            }
        }
        return result;
    }

    private String invalidClassNameDetail(final String value) {
        if (value.isEmpty()) {
            return "literal=<empty>";
        }
        if (value.isBlank()) {
            return "literal=<blank>";
        }
        return "literal=" + value;
    }

    private boolean matches(final DynamicCallEvidence value,
            final ChangePoint point) {
        return point.getOwner().equals(value.targetOwner())
                && point.getName().equals(value.targetName())
                && point.getOldDescriptor().equals(
                value.targetDescriptor());
    }

    private boolean matchesMethod(
            final MethodReference method,
            final String expectedOwner,
            final String expectedName,
            final String expectedDescriptor) {
        return method != null && expectedOwner.equals(owner(method))
                && java.util.Objects.equals(expectedName,
                method.getName().toString())
                && java.util.Objects.equals(expectedDescriptor,
                method.getDescriptor().toString());
    }

    private boolean matchesField(
            final FieldReference field,
            final ChangePoint point) {
        if (!point.getOwner().equals(owner(field.getDeclaringClass()))
                || !java.util.Objects.equals(point.getName(),
                field.getName().toString())) {
            return false;
        }
        final String value = descriptor(field.getFieldType());
        return java.util.Objects.equals(point.getOldDescriptor(), value)
                || java.util.Objects.equals(point.getNewDescriptor(), value);
    }

    private Set<String> referencedTypes(
            final SSAInstruction instruction) {
        final Set<String> result = new LinkedHashSet<>();
        instruction.getExceptionTypes().forEach(type -> addType(result, type));
        if (instruction instanceof SSANewInstruction allocation) {
            addType(result, allocation.getConcreteType());
        }
        if (instruction instanceof SSACheckCastInstruction cast) {
            for (TypeReference type : cast.getDeclaredResultTypes()) {
                addType(result, type);
            }
        }
        if (instruction instanceof SSAInstanceofInstruction instanceOf) {
            addType(result, instanceOf.getCheckedType());
        }
        if (instruction instanceof SSAArrayReferenceInstruction array) {
            addType(result, array.getElementType());
        }
        if (instruction instanceof SSALoadMetadataInstruction metadata) {
            addType(result, metadata.getType());
            if (metadata.getToken() instanceof TypeReference type) {
                addType(result, type);
            }
        }
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            addMethodTypes(result, invoke.getDeclaredTarget());
        }
        if (instruction instanceof SSAFieldAccessInstruction field) {
            addType(result, field.getDeclaredField().getDeclaringClass());
            addType(result, field.getDeclaredField().getFieldType());
        }
        return result;
    }

    private void addMethodTypes(
            final Set<String> result,
            final MethodReference method) {
        addType(result, method.getDeclaringClass());
        addType(result, method.getReturnType());
        for (int index = 0;
                index < method.getNumberOfParameters(); index++) {
            addType(result, method.getParameterType(index));
        }
    }

    private void addType(
            final Set<String> result,
            final TypeReference type) {
        if (type == null || type.isPrimitiveType()) {
            return;
        }
        final TypeReference value = type.isArrayType()
                ? type.getInnermostElementType() : type;
        if (!value.isPrimitiveType()) {
            result.add(owner(value));
        }
    }

    private int pc(final SSAInstruction instruction) {
        return instruction instanceof SSAAbstractInvokeInstruction invoke
                ? invoke.getProgramCounter() : instruction.iIndex();
    }

    private boolean isAdded(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_ADDED
                || kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.FIELD_ADDED;
    }

    private String owner(final MethodReference method) {
        return owner(method.getDeclaringClass());
    }

    private String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private String descriptor(final TypeReference type) {
        final String value = type.getName().toString();
        if (value.startsWith("L") || value.startsWith("[L")) {
            return value.endsWith(";") ? value : value + ";";
        }
        return value;
    }

    /** Private JVM-identity ChangePoint lookup for the Evidence phase. */
    private final class ChangeMatcherIndex {

        /** Changed reachable method declarations. */
        private final Map<String, List<BoundChangePoint>> declarations =
                new LinkedHashMap<>();

        /** Removed, descriptor-changed, or narrowed method references. */
        private final Map<String, List<BoundChangePoint>> methods =
                new LinkedHashMap<>();

        /** Removed, descriptor-changed, or narrowed field references. */
        private final Map<String, List<BoundChangePoint>> fields =
                new LinkedHashMap<>();

        /** Removed or narrowed class references. */
        private final Map<String, List<BoundChangePoint>> classes =
                new LinkedHashMap<>();

        /** @param unit canonical Module input */
        ChangeMatcherIndex(final ModuleAnalysisUnit unit) {
            for (BoundChangePoint point : unit.getChangePoints()) {
                final ChangePoint change = point.getChangePoint();
                switch (change.getKind()) {
                    case METHOD_BODY_CHANGED -> add(declarations,
                            methodKey(change.getOwner(), change.getName(),
                                    change.getNewDescriptor()), point);
                    case METHOD_REMOVED, METHOD_DESCRIPTOR_CHANGED ->
                            add(methods, methodKey(change.getOwner(),
                                    change.getName(),
                                    change.getOldDescriptor()), point);
                    case METHOD_ACCESS_NARROWED -> add(methods,
                            methodKey(change.getOwner(), change.getName(),
                                    change.getNewDescriptor()), point);
                    case FIELD_REMOVED, FIELD_DESCRIPTOR_CHANGED,
                            FIELD_ACCESS_NARROWED -> {
                        add(fields, fieldKey(change.getOwner(),
                                change.getName(), change.getOldDescriptor()),
                                point);
                        if (!java.util.Objects.equals(
                                change.getOldDescriptor(),
                                change.getNewDescriptor())) {
                            add(fields, fieldKey(change.getOwner(),
                                    change.getName(),
                                    change.getNewDescriptor()), point);
                        }
                    }
                    case CLASS_REMOVED, CLASS_ACCESS_NARROWED ->
                            add(classes, change.getOwner(), point);
                    default -> {
                        // Change kind has no reachable bytecode matcher.
                    }
                }
            }
        }

        List<BoundChangePoint> methodDeclarations(
                final MethodReference method) {
            return declarations.getOrDefault(methodKey(method), List.of());
        }

        List<BoundChangePoint> methodReferences(
                final MethodReference method) {
            return methods.getOrDefault(methodKey(method), List.of());
        }

        List<BoundChangePoint> fieldReferences(
                final FieldReference field) {
            return fields.getOrDefault(fieldKey(field), List.of());
        }

        List<BoundChangePoint> classReferences(final String owner) {
            return classes.getOrDefault(owner, List.of());
        }

        private void add(
                final Map<String, List<BoundChangePoint>> target,
                final String key,
                final BoundChangePoint point) {
            if (key == null) {
                return;
            }
            target.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(point);
        }

        private String methodKey(final MethodReference method) {
            return methodKey(owner(method), method.getName().toString(),
                    method.getDescriptor().toString());
        }

        private String methodKey(
                final String owner,
                final String name,
                final String methodDescriptor) {
            return owner + "#" + name + methodDescriptor;
        }

        private String fieldKey(final FieldReference field) {
            return fieldKey(owner(field.getDeclaringClass()),
                    field.getName().toString(),
                    descriptor(field.getFieldType()));
        }

        private String fieldKey(
                final String owner,
                final String name,
                final String fieldDescriptor) {
            return owner + "#" + name + ":" + fieldDescriptor;
        }
    }

    /**
     * Internal argument object keeping evidence creation deterministic.
     *
     * @param unit module input
     * @param node exact caller
     * @param instruction exact reference instruction
     * @param ownership target ownership
     * @param target reference target
     * @param kind evidence target category
     * @param mechanism discovery mechanism
     * @param pc bytecode program counter
     * @param detail stable detail
     */
    private record EvidenceInput(
            ModuleAnalysisUnit unit,
            CGNode node,
            Optional<SSAInstruction> instruction,
            ClassOwnershipIndex ownership,
            ReferenceTarget target,
            EvidenceKind kind,
            EvidenceMechanism mechanism,
            int pc,
            String detail) {
    }
}
