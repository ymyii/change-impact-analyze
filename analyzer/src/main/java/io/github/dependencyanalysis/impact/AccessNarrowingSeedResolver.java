package io.github.dependencyanalysis.impact;

import com.ibm.wala.analysis.typeInference.ConeType;
import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves access-narrowing seeds from the frozen evidence index. */
final class AccessNarrowingSeedResolver
        implements ChangePointSeedResolver {

    @Override
    public boolean supports(final ChangePointKind kind) {
        return kind.isAccessNarrowing();
    }

    @Override
    public ChangePointSeedResolution resolve(
            final ChangePointSeedRequest request) {
        final ModuleId moduleId = request.moduleId();
        final ChangePoint point = request.point();
        final ModuleCallGraphSession session = request.session();
        if (!point.getKind().isAccessNarrowing()) {
            throw new IllegalArgumentException(
                    "Access resolver requires an access ChangePoint");
        }
        final Set<ImpactSeed> seeds = new LinkedHashSet<>();
        final Map<String, ImpactEvidence> evidence = new LinkedHashMap<>();
        final Set<QueryLimitation> limitations = new LinkedHashSet<>();
        final AccessSeedContext context = new AccessSeedContext(
                moduleId, point, session,
                new JvmAccessChecker(session.getHierarchy()),
                seeds, evidence, limitations, new HashMap<>());
        for (ReferenceEvidence reference : request.evidence().evidence()) {
            observeReference(context, reference);
        }
        final List<ImpactSeed> ordered = seeds.stream()
                .sorted(seedComparator()).toList();
        final List<ImpactEvidence> observations = evidence.values().stream()
                .sorted(Comparator.comparing(ImpactEvidence::stableKey))
                .toList();
        final ReferenceObservation observation = ordered.isEmpty()
                ? observations.isEmpty() ? ReferenceObservation.NONE
                : ReferenceObservation.ACCESSIBLE_ONLY
                : ReferenceObservation.IMPACTING;
        return new ChangePointSeedResolution(
                ordered, observation, observations,
                limitations.stream().sorted().toList());
    }

    private void observeReference(
            final AccessSeedContext context,
            final ReferenceEvidence evidence) {
        final EvidenceAnchor rawAnchor = evidence.anchor().orElse(null);
        final MethodEvidenceAnchor anchor = rawAnchor
                instanceof MethodEvidenceAnchor method ? method : null;
        if (anchor == null) {
            return;
        }
        final SSAInstruction instruction = anchor.instruction().orElse(null);
        if (instruction != null) {
            observeInstruction(context, anchor.node(), instruction,
                    evidence);
            return;
        }
        observeDynamicAccess(context, anchor.node(), evidence);
    }

    private void observeInstruction(
            final AccessSeedContext context,
            final CGNode node,
            final SSAInstruction instruction,
            final ReferenceEvidence evidence) {
        final ChangePointKind kind = context.point().getKind();
        if (kind == ChangePointKind.METHOD_ACCESS_NARROWED
                && instruction
                instanceof SSAAbstractInvokeInstruction invoke) {
            observeMethodAccess(context, node, invoke, evidence);
        } else if (kind == ChangePointKind.FIELD_ACCESS_NARROWED
                && instruction instanceof SSAFieldAccessInstruction field) {
            observeFieldAccess(context, node, field, evidence);
        } else if (kind == ChangePointKind.CLASS_ACCESS_NARROWED
                && referencesType(instruction, context.point().getOwner())) {
            observeClassAccess(context, node, evidence);
        }
    }

    private void observeMethodAccess(
            final AccessSeedContext context,
            final CGNode node,
            final SSAAbstractInvokeInstruction invoke,
            final ReferenceEvidence evidence) {
        final IMethod declaration = context.session().getHierarchy()
                .resolveMethod(invoke.getDeclaredTarget());
        if (declaration == null) {
            if (sameMemberSignature(invoke.getDeclaredTarget(),
                    context.point())) {
                context.limitations().add(queryLimitation(
                        "ACCESS_DECLARATION_UNRESOLVED",
                        changeLocation(context.point()),
                        "Method declaration is absent: "
                                + invoke.getDeclaredTarget()));
            }
            return;
        }
        if (!matchesMethod(
                declaration.getReference(), context.point().getOwner(),
                context.point().getName(),
                context.point().getNewDescriptor())) {
            return;
        }
        final IClass symbolicOwner = context.session().getHierarchy()
                .lookupClass(invoke.getDeclaredTarget().getDeclaringClass());
        if (symbolicOwner == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_TARGET_TYPE_UNRESOLVED",
                    changeLocation(context.point()),
                    "Method symbolic owner is absent: "
                            + invoke.getDeclaredTarget().getDeclaringClass()));
            return;
        }
        final JvmReferenceKind kind = declaration.isInit()
                ? JvmReferenceKind.CONSTRUCTOR
                : declaration.isStatic()
                ? JvmReferenceKind.METHOD_STATIC
                : JvmReferenceKind.METHOD_INSTANCE;
        observeAccess(context, node, declaration.getDeclaringClass(),
                symbolicOwner, new AccessReference(kind,
                declaration.isStatic() ? ReceiverType.notApplicable()
                        : receiverType(context, node, invoke.getReceiver(),
                                invoke.isSpecial()
                                        && !declaration.isInit()),
                evidence,
                methodIdentity(invoke.getDeclaredTarget())));
    }

    private void observeFieldAccess(
            final AccessSeedContext context,
            final CGNode node,
            final SSAFieldAccessInstruction instruction,
            final ReferenceEvidence evidence) {
        final IField declaration = context.session().getHierarchy()
                .resolveField(instruction.getDeclaredField());
        if (declaration == null) {
            if (sameFieldSignature(instruction.getDeclaredField(),
                    context.point())) {
                context.limitations().add(queryLimitation(
                        "ACCESS_DECLARATION_UNRESOLVED",
                        changeLocation(context.point()),
                        "Field declaration is absent: "
                                + instruction.getDeclaredField()));
            }
            return;
        }
        if (!context.point().getOwner().equals(owner(
                        declaration.getDeclaringClass().getReference()))
                || !Objects.equals(context.point().getName(),
                        declaration.getName().toString())
                || !Objects.equals(context.point().getNewDescriptor(),
                        descriptor(declaration.getFieldTypeReference()))) {
            return;
        }
        final IClass symbolicOwner = context.session().getHierarchy()
                .lookupClass(instruction.getDeclaredField()
                        .getDeclaringClass());
        if (symbolicOwner == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_TARGET_TYPE_UNRESOLVED",
                    changeLocation(context.point()),
                    "Field symbolic owner is absent: "
                            + instruction.getDeclaredField()
                            .getDeclaringClass()));
            return;
        }
        observeAccess(context, node, declaration.getDeclaringClass(),
                symbolicOwner, new AccessReference(
                instruction.isStatic() ? JvmReferenceKind.FIELD_STATIC
                        : JvmReferenceKind.FIELD_INSTANCE,
                instruction.isStatic() ? ReceiverType.notApplicable()
                        : receiverType(context, node,
                                instruction.getRef(), false),
                evidence,
                instruction.getDeclaredField().toString()));
    }

    private void observeClassAccess(
            final AccessSeedContext context,
            final CGNode node,
            final ReferenceEvidence evidence) {
        final IClass target = lookupClass(
                context.session(), context.point().getOwner());
        if (target == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_TARGET_TYPE_UNRESOLVED",
                    changeLocation(context.point()),
                    "Changed class is absent: "
                            + context.point().getOwner()));
            return;
        }
        observeAccess(context, node, target, target,
                new AccessReference(JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable(),
                        evidence,
                        context.point().getOwner()));
    }

    private void observeDynamicAccess(
            final AccessSeedContext context,
            final CGNode node,
            final ReferenceEvidence evidence) {
        final ReferenceTarget target = evidence.target();
        final IClass symbolicOwner = lookupClass(
                context.session(), target.owner());
        if (symbolicOwner == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_TARGET_TYPE_UNRESOLVED",
                    changeLocation(context.point()),
                    "Dynamic symbolic owner is absent: "
                            + target.owner()));
            return;
        }
        if (context.point().getKind()
                == ChangePointKind.CLASS_ACCESS_NARROWED) {
            observeAccess(context, node, symbolicOwner,
                    symbolicOwner, new AccessReference(
                    JvmReferenceKind.CLASS,
                    ReceiverType.notApplicable(), evidence,
                    evidence.detail()));
            return;
        }
        if (context.point().getKind()
                == ChangePointKind.FIELD_ACCESS_NARROWED) {
            if (evidence.kind() == EvidenceKind.FIELD_REFERENCE) {
                observeDynamicField(context, node, evidence,
                        symbolicOwner);
            }
            return;
        }
        if (context.point().getKind()
                != ChangePointKind.METHOD_ACCESS_NARROWED
                || evidence.kind() != EvidenceKind.METHOD_REFERENCE) {
            return;
        }
        final MethodReference reference = MethodReference.findOrCreate(
                symbolicOwner.getReference(),
                Atom.findOrCreateUnicodeAtom(target.name()),
                Descriptor.findOrCreateUTF8(target.descriptor()));
        final IMethod declaration = context.session().getHierarchy()
                .resolveMethod(reference);
        if (declaration == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_DECLARATION_UNRESOLVED",
                    changeLocation(context.point()),
                    "Dynamic method declaration is absent: " + reference));
            return;
        }
        if (!matchesMethod(declaration.getReference(),
                context.point().getOwner(), context.point().getName(),
                context.point().getNewDescriptor())) {
            return;
        }
        final JvmReferenceKind referenceKind = declaration.isInit()
                ? JvmReferenceKind.CONSTRUCTOR
                : declaration.isStatic()
                ? JvmReferenceKind.METHOD_STATIC
                : JvmReferenceKind.METHOD_INSTANCE;
        observeAccess(context, node,
                declaration.getDeclaringClass(), symbolicOwner,
                new AccessReference(referenceKind,
                        referenceKind.isInstance()
                                ? ReceiverType.unknown()
                                : ReceiverType.notApplicable(),
                        evidence, evidence.detail()));
    }

    private void observeDynamicField(
            final AccessSeedContext context,
            final CGNode node,
            final ReferenceEvidence evidence,
            final IClass symbolicOwner) {
        final ReferenceTarget target = evidence.target();
        final TypeReference fieldType = TypeReference.findOrCreate(
                symbolicOwner.getClassLoader().getReference(),
                TypeName.string2TypeName(target.descriptor()));
        final FieldReference reference = FieldReference.findOrCreate(
                symbolicOwner.getReference(),
                Atom.findOrCreateUnicodeAtom(target.name()),
                fieldType);
        final IField declaration = context.session().getHierarchy()
                .resolveField(reference);
        if (declaration == null) {
            context.limitations().add(queryLimitation(
                    "ACCESS_DECLARATION_UNRESOLVED",
                    changeLocation(context.point()),
                    "Dynamic field declaration is absent: " + reference));
            return;
        }
        if (!context.point().getOwner().equals(owner(
                declaration.getDeclaringClass().getReference()))) {
            return;
        }
        final JvmReferenceKind referenceKind = declaration.isStatic()
                ? JvmReferenceKind.FIELD_STATIC
                : JvmReferenceKind.FIELD_INSTANCE;
        observeAccess(context, node,
                declaration.getDeclaringClass(), symbolicOwner,
                new AccessReference(referenceKind,
                        referenceKind.isInstance()
                                ? ReceiverType.unknown()
                                : ReceiverType.notApplicable(),
                        evidence, evidence.detail()));
    }

    private void observeAccess(
            final AccessSeedContext context,
            final CGNode node,
            final IClass declaration,
            final IClass symbolicOwner,
            final AccessReference reference) {
        final AccessTransition transition = context.point()
                .getAccessTransition().orElseThrow();
        final AccessCheckResult result = context.checker().check(
                new AccessCheckRequest(
                        node.getMethod().getDeclaringClass(), declaration,
                        symbolicOwner, transition.newAccess(),
                        reference.kind(), reference.receiver()));
        final AccessReferenceEvidence detail =
                new AccessReferenceEvidence(transition,
                        result.decision(), result.reason(),
                        methodIdentity(node.getMethod().getReference()),
                        owner(declaration.getReference()),
                        owner(symbolicOwner.getReference()),
                        reference.receiver().stableKey(),
                        reference.identity());
        context.evidence().putIfAbsent(detail.stableKey(), detail);
        if (result.decision() != AccessDecision.ACCESSIBLE) {
            context.seeds().add(new ImpactSeed(queryNode(
                    context.moduleId(), node, context.session()),
                    reference.evidence()));
        }
    }

    private ReceiverType receiverType(
            final AccessSeedContext context,
            final CGNode node,
            final int value,
            final boolean superInvoke) {
        if (superInvoke) {
            return ReceiverType.superReceiver();
        }
        final IR ir = node.getIR();
        if (!node.getMethod().isStatic()
                && value == ir.getSymbolTable().getParameter(0)) {
            return ReceiverType.thisReceiver();
        }
        final TypeInference inference = context.typeInferences()
                .computeIfAbsent(node,
                        ignored -> TypeInference.make(ir, true));
        final TypeAbstraction type = inference.getType(value);
        if (type instanceof PointType point) {
            return ReceiverType.point(point.getType());
        }
        if (type instanceof ConeType cone) {
            return ReceiverType.cone(cone.getType());
        }
        return ReceiverType.unknown();
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
        if (instruction instanceof SSAFieldAccessInstruction fieldAccess) {
            final FieldReference field = fieldAccess.getDeclaredField();
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
            final TypeReference type,
            final String expectedOwner) {
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

    private boolean sameMemberSignature(
            final MethodReference method,
            final ChangePoint point) {
        return Objects.equals(point.getName(), method.getName().toString())
                && Objects.equals(point.getNewDescriptor(),
                method.getDescriptor().toString());
    }

    private boolean sameFieldSignature(
            final FieldReference field,
            final ChangePoint point) {
        return Objects.equals(point.getName(), field.getName().toString())
                && Objects.equals(point.getNewDescriptor(),
                descriptor(field.getFieldType()));
    }

    private IClass lookupClass(
            final ModuleCallGraphSession session,
            final String expectedOwner) {
        for (IClass type : session.getHierarchy()) {
            if (expectedOwner.equals(owner(type.getReference()))) {
                return type;
            }
        }
        return null;
    }

    private QueryLimitation queryLimitation(
            final String code,
            final String location,
            final String detail) {
        return new QueryLimitation(code,
                ModuleAnalysisReason.INCONCLUSIVE_SCOPE_VALIDATION,
                location, detail);
    }

    private String changeLocation(final ChangePoint point) {
        return point.getKind() + "|" + point.getOwner() + "|"
                + point.getName() + "|" + point.getNewDescriptor() + "|"
                + point.getAccessTransition()
                .map(AccessTransition::stableKey).orElse("");
    }

    private WalaQueryNode queryNode(
            final ModuleId moduleId,
            final CGNode node,
            final ModuleCallGraphSession session) {
        final IMethod method = node.getMethod();
        final String methodOwner = owner(method.getReference());
        final ClassOwnership ownership = session.getOwnership()
                .ownershipOf(methodOwner);
        final CodeOrigin origin = session.originOf(
                method.getDeclaringClass());
        final String module = origin == CodeOrigin.PROJECT
                ? moduleId.stableKey() : origin.name();
        final String source = ownership == null
                ? origin == CodeOrigin.JDK ? "<jdk>" : "<synthetic>"
                : ownership.getSource().toString();
        return new WalaQueryNode(node, new MethodId(methodOwner,
                method.getName().toString(),
                method.getDescriptor().toString(), module, source), origin);
    }

    private Comparator<ImpactSeed> seedComparator() {
        return Comparator.comparing((ImpactSeed seed) ->
                        methodIdentity(seed.node().methodId()))
                .thenComparingInt(seed -> queryNodeNumber(seed.node()))
                .thenComparing(seed -> seed.evidence().stableKey());
    }

    private int queryNodeNumber(final QueryNode node) {
        return node instanceof WalaQueryNode wala
                ? wala.walaNode().getGraphNodeId() : Integer.MAX_VALUE;
    }

    private String methodIdentity(final MethodReference method) {
        return owner(method) + "#" + method.getName() + "#"
                + method.getDescriptor();
    }

    private String methodIdentity(final MethodId method) {
        return method.owner() + "#" + method.name() + "#"
                + method.descriptor();
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
     * Query-local mutable accumulation owned only by this resolver call.
     *
     * @param moduleId current Module
     * @param point access ChangePoint
     * @param session target graph session
     * @param checker pure JVM access checker
     * @param seeds impacting seeds
     * @param evidence typed observations
     * @param limitations query limitations
     * @param typeInferences verifier inference cache by Context
     */
    private record AccessSeedContext(
            ModuleId moduleId,
            ChangePoint point,
            ModuleCallGraphSession session,
            JvmAccessChecker checker,
            Set<ImpactSeed> seeds,
            Map<String, ImpactEvidence> evidence,
            Set<QueryLimitation> limitations,
            Map<CGNode, TypeInference> typeInferences) {
    }

    /**
     * Exact access-operation facts passed to the pure checker.
     *
     * @param kind JVM reference kind
     * @param receiver verifier-level receiver type
     * @param evidence terminal reference evidence
     * @param identity symbolic reference identity
     */
    private record AccessReference(
            JvmReferenceKind kind,
            ReceiverType receiver,
            ReferenceEvidence evidence,
            String identity) {
    }
}
