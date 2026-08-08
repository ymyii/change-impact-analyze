package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.DynamicCallEvidence;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves existing member/body and removed member reference seeds. */
final class ExistingMemberSeedResolver
        implements ChangePointSeedResolver {

    @Override
    public boolean supports(final ChangePointKind kind) {
        return kind == ChangePointKind.METHOD_BODY_CHANGED
                || kind == ChangePointKind.METHOD_REMOVED
                || kind == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                || kind == ChangePointKind.FIELD_REMOVED
                || kind == ChangePointKind.FIELD_DESCRIPTOR_CHANGED;
    }

    @Override
    public ChangePointSeedResolution resolve(
            final ChangePointSeedRequest request) {
        final ChangePoint point = request.point();
        final Set<ImpactSeed> result = new LinkedHashSet<>();
        if (point.getKind() == ChangePointKind.METHOD_BODY_CHANGED) {
            for (CGNode node : request.session().getGraph()) {
                if (matchesMethod(node.getMethod().getReference(),
                        point.getOwner(), point.getName(),
                        point.getNewDescriptor())) {
                    result.add(new ImpactSeed(queryNode(request, node),
                            EdgeKind.METHOD_CHANGE,
                            new TextImpactEvidence("target="
                                    + methodIdentity(node.getMethod()
                                    .getReference()))));
                }
            }
        }
        if (point.getKind() == ChangePointKind.METHOD_REMOVED
                || point.getKind()
                == ChangePointKind.METHOD_DESCRIPTOR_CHANGED) {
            for (ReachableReferenceCollector.ReachableInstruction fact
                    : request.reachable().instructions()) {
                if (!(fact.instruction()
                        instanceof SSAAbstractInvokeInstruction invoke)) {
                    continue;
                }
                final MethodReference target = invoke.getDeclaredTarget();
                if (matchesMethod(target, point.getOwner(), point.getName(),
                        point.getOldDescriptor())) {
                    result.add(new ImpactSeed(queryNode(
                            request, fact.caller()),
                            EdgeKind.DECLARED_INVOKE_REFERENCE,
                            new TextImpactEvidence(
                                    "declaredTarget="
                                            + methodIdentity(target))));
                }
            }
            for (DynamicCallEvidence evidence
                    : request.dynamicEvidence().find(
                            point.getOwner(), point.getName(),
                            point.getOldDescriptor())) {
                result.add(new ImpactSeed(queryNode(
                        request, evidence.caller()), evidence.kind(),
                        new TextImpactEvidence(evidence.detail())));
            }
        } else if (point.getKind() == ChangePointKind.FIELD_REMOVED
                || point.getKind()
                == ChangePointKind.FIELD_DESCRIPTOR_CHANGED) {
            for (ReachableReferenceCollector.ReachableInstruction fact
                    : request.reachable().instructions()) {
                if (!(fact.instruction()
                        instanceof SSAFieldAccessInstruction fieldAccess)) {
                    continue;
                }
                final FieldReference field = fieldAccess.getDeclaredField();
                if (matchesField(field, point)) {
                    result.add(new ImpactSeed(queryNode(
                            request, fact.caller()),
                            EdgeKind.FIELD_REFERENCE,
                            new TextImpactEvidence(
                                    "declaredField=" + field)));
                }
            }
        }
        final List<ImpactSeed> seeds = result.stream()
                .sorted(seedComparator()).toList();
        return new ChangePointSeedResolution(seeds, seeds.isEmpty()
                ? ReferenceObservation.NONE : ReferenceObservation.IMPACTING,
                List.of(), List.of());
    }

    private WalaQueryNode queryNode(
            final ChangePointSeedRequest request,
            final CGNode node) {
        final ModuleCallGraphSession session = request.session();
        final IMethod method = node.getMethod();
        final String methodOwner = owner(method.getReference());
        final ClassOwnership ownership = session.getOwnership()
                .ownershipOf(methodOwner);
        final CodeOrigin origin = session.originOf(
                method.getDeclaringClass());
        final String module = origin == CodeOrigin.PROJECT
                ? request.moduleId().stableKey() : origin.name();
        final String source = ownership == null
                ? origin == CodeOrigin.JDK ? "<jdk>" : "<synthetic>"
                : ownership.getSource().toString();
        return new WalaQueryNode(node, new MethodId(methodOwner,
                method.getName().toString(),
                method.getDescriptor().toString(), module, source), origin);
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
            final FieldReference field,
            final ChangePoint point) {
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

    private Comparator<ImpactSeed> seedComparator() {
        return Comparator.comparing((ImpactSeed seed) ->
                        methodIdentity(seed.node().methodId()))
                .thenComparingInt(seed -> seed.node()
                        instanceof WalaQueryNode wala
                        ? wala.walaNode().getGraphNodeId()
                        : Integer.MAX_VALUE)
                .thenComparing(seed -> seed.kind().name());
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
}
