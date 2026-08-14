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

    /** Bounded caller-local constant resolver. */
    private final LocalConstantResolver constants =
            new LocalConstantResolver();

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
        final Set<ModelLimitation> limitations = new LinkedHashSet<>();
        final int[] localCounts = new int[2];
        final CollectionContext context = new CollectionContext(
                unit, ownership, byOwner, evidence, limitations,
                capabilities, localCounts);
        collectResourceAnchors(unit, evidence);
        unit.getServiceLoaderResourceIssues().forEach(issue ->
                limitations.add(serviceLoaderLimitation(
                        issue.code(), issue.location(), issue.detail())));
        for (CGNode node : graph) {
            if (node.equals(graph.getFakeRootNode())
                    || node.equals(graph.getFakeWorldClinitNode())) {
                continue;
            }
            collectMethodDeclaration(
                    unit, node, ownership, evidence);
            if (!bodyAvailable.test(node.getMethod())) {
                continue;
            }
            final IR ir = node.getIR();
            if (ir == null) {
                continue;
            }
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction == null) {
                    continue;
                }
                collectInstruction(context, node, instruction);
            }
        }
        collectStructural(unit, structuralReferences, evidence);
        collectDynamic(unit, ownership, dynamicEvidence, evidence);
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
        return new ChangePointEvidenceIndex(resolutions,
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

    private void collectStructural(
            final ModuleAnalysisUnit unit,
            final StructuralReferenceIndex structuralReferences,
            final Map<BoundChangePoint, List<ReferenceEvidence>> result) {
        final Map<String, List<BoundChangePoint>> changes = byOwner(unit);
        for (StructuralReference reference : structuralReferences
                .references()) {
            for (BoundChangePoint point : changes.getOrDefault(
                    reference.getChangedClass(), List.of())) {
                final ChangePointKind kind = point.getChangePoint().getKind();
                if (kind != ChangePointKind.CLASS_REMOVED
                        && kind != ChangePointKind.CLASS_ACCESS_NARROWED) {
                    continue;
                }
                result.get(point).add(new ReferenceEvidence(
                        Optional.of(new StructuralEvidenceAnchor(reference)),
                        new ReferenceTarget(reference.getChangedClass(),
                                reference.getReferencingMember(), ""),
                        EvidenceKind.STRUCTURAL_REFERENCE,
                        EvidenceMechanism.STRUCTURAL_METADATA,
                        new EvidenceLocation(
                                reference.getReferencingClass(), -1),
                        reference.getEvidence()));
            }
        }
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
            final ModuleAnalysisUnit unit,
            final CGNode node,
            final ClassOwnershipIndex ownership,
            final Map<BoundChangePoint, List<ReferenceEvidence>> result) {
        for (BoundChangePoint point : unit.getChangePoints()) {
            final ChangePoint change = point.getChangePoint();
            if (change.getKind() == ChangePointKind.METHOD_BODY_CHANGED
                    && matchesMethod(node.getMethod().getReference(),
                    change.getOwner(), change.getName(),
                    change.getNewDescriptor())) {
                result.get(point).add(evidence(new EvidenceInput(
                        unit, node, Optional.empty(), ownership,
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
            collectField(context.unit(), node, field, context.ownership(),
                    context.result());
        }
        for (Map.Entry<String, List<BoundChangePoint>> entry
                : context.byOwner().entrySet()) {
            if (!referencesType(instruction, entry.getKey())) {
                continue;
            }
            for (BoundChangePoint point : entry.getValue()) {
                final ChangePointKind kind = point.getChangePoint().getKind();
                if (kind == ChangePointKind.CLASS_REMOVED
                        || kind == ChangePointKind.CLASS_ACCESS_NARROWED) {
                    context.result().get(point).add(evidence(
                            new EvidenceInput(
                            context.unit(), node, Optional.of(instruction),
                            context.ownership(),
                            new ReferenceTarget(entry.getKey(), "", ""),
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
        for (BoundChangePoint point : context.unit().getChangePoints()) {
            final ChangePoint change = point.getChangePoint();
            if ((change.getKind() == ChangePointKind.METHOD_REMOVED
                    || change.getKind()
                    == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                    || change.getKind()
                    == ChangePointKind.METHOD_ACCESS_NARROWED)
                    && matchesMethod(target, change.getOwner(),
                    change.getName(), change.getKind()
                            == ChangePointKind.METHOD_ACCESS_NARROWED
                            ? change.getNewDescriptor()
                            : change.getOldDescriptor())) {
                context.result().get(point).add(evidence(new EvidenceInput(
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
                context.result().get(point).add(evidence(new EvidenceInput(
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
                context.result().get(point).add(evidence(new EvidenceInput(
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
                context.result().get(point).add(evidence(new EvidenceInput(
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
            final ModuleAnalysisUnit unit,
            final CGNode node,
            final SSAFieldAccessInstruction field,
            final ClassOwnershipIndex ownership,
            final Map<BoundChangePoint, List<ReferenceEvidence>> result) {
        final FieldReference target = field.getDeclaredField();
        for (BoundChangePoint point : unit.getChangePoints()) {
            final ChangePoint change = point.getChangePoint();
            if ((change.getKind() == ChangePointKind.FIELD_REMOVED
                    || change.getKind()
                    == ChangePointKind.FIELD_DESCRIPTOR_CHANGED
                    || change.getKind()
                    == ChangePointKind.FIELD_ACCESS_NARROWED)
                    && matchesField(target, change)) {
                result.get(point).add(evidence(new EvidenceInput(
                        unit, node, Optional.of(field), ownership,
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
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership,
            final DynamicCallEvidenceIndex dynamic,
            final Map<BoundChangePoint, List<ReferenceEvidence>> result) {
        for (DynamicCallEvidence value : dynamic.all()) {
            for (BoundChangePoint point : unit.getChangePoints()) {
                final ChangePoint change = point.getChangePoint();
                if ((change.getKind() == ChangePointKind.METHOD_REMOVED
                        || change.getKind()
                        == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                        || change.getKind()
                        == ChangePointKind.METHOD_ACCESS_NARROWED)
                        && matches(value, change)) {
                    result.get(point).add(evidence(new EvidenceInput(
                            unit, value.caller(), Optional.empty(), ownership,
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
                    result.get(point).add(evidence(new EvidenceInput(
                            unit, value.caller(), Optional.empty(), ownership,
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
                    result.get(point).add(evidence(new EvidenceInput(
                            unit, value.caller(), Optional.empty(), ownership,
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
     * @param result mutable evidence accumulator
     * @param limitations mutable limitation accumulator
     * @param capabilities effective strategy capabilities
     * @param localCounts success/unresolved caller-local counts
     */
    private record CollectionContext(
            ModuleAnalysisUnit unit,
            ClassOwnershipIndex ownership,
            Map<String, List<BoundChangePoint>> byOwner,
            Map<BoundChangePoint, List<ReferenceEvidence>> result,
            Set<ModelLimitation> limitations,
            CallGraphStrategyCapabilities capabilities,
            int[] localCounts) {
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

    private boolean referencesType(
            final SSAInstruction instruction,
            final String expectedOwner) {
        if (instruction.getExceptionTypes().stream()
                .anyMatch(type -> matchesType(type, expectedOwner))) {
            return true;
        }
        if (instruction instanceof SSANewInstruction allocation) {
            return matchesType(allocation.getConcreteType(), expectedOwner);
        }
        if (instruction instanceof SSACheckCastInstruction cast) {
            for (TypeReference type : cast.getDeclaredResultTypes()) {
                if (matchesType(type, expectedOwner)) {
                    return true;
                }
            }
        }
        if (instruction instanceof SSAInstanceofInstruction instanceOf) {
            return matchesType(instanceOf.getCheckedType(), expectedOwner);
        }
        if (instruction instanceof SSAArrayReferenceInstruction array) {
            return matchesType(array.getElementType(), expectedOwner);
        }
        if (instruction instanceof SSALoadMetadataInstruction metadata) {
            return matchesType(metadata.getType(), expectedOwner)
                    || metadata.getToken() instanceof TypeReference type
                    && matchesType(type, expectedOwner);
        }
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            return methodReferencesType(
                    invoke.getDeclaredTarget(), expectedOwner);
        }
        if (instruction instanceof SSAFieldAccessInstruction field) {
            return matchesType(field.getDeclaredField().getDeclaringClass(),
                    expectedOwner)
                    || matchesType(field.getDeclaredField().getFieldType(),
                    expectedOwner);
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
            final TypeReference type,
            final String expectedOwner) {
        if (type == null || type.isPrimitiveType()) {
            return false;
        }
        final TypeReference value = type.isArrayType()
                ? type.getInnermostElementType() : type;
        return expectedOwner.equals(owner(value));
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
