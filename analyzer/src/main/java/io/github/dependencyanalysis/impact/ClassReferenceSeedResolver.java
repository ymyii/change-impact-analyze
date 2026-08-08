package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
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

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves reachable IR class references for removed classes. */
final class ClassReferenceSeedResolver
        implements ChangePointSeedResolver {

    @Override
    public boolean supports(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_REMOVED;
    }

    @Override
    public ChangePointSeedResolution resolve(
            final ChangePointSeedRequest request) {
        final Set<ImpactSeed> result = new LinkedHashSet<>();
        for (ReachableReferenceCollector.ReachableInstruction fact
                : request.reachable().instructions()) {
            if (referencesType(fact.instruction(),
                    request.point().getOwner())) {
                result.add(new ImpactSeed(queryNode(request, fact.caller()),
                        EdgeKind.TYPE_REFERENCE,
                        new TextImpactEvidence("referencedType="
                                + request.point().getOwner())));
            }
        }
        final List<ImpactSeed> seeds = result.stream()
                .sorted(seedComparator()).toList();
        return new ChangePointSeedResolution(seeds, seeds.isEmpty()
                ? ReferenceObservation.NONE : ReferenceObservation.IMPACTING,
                List.of(), List.of());
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

    private Comparator<ImpactSeed> seedComparator() {
        return Comparator.comparing((ImpactSeed seed) ->
                        methodIdentity(seed.node().methodId()))
                .thenComparingInt(seed -> seed.node()
                        instanceof WalaQueryNode wala
                        ? wala.walaNode().getGraphNodeId()
                        : Integer.MAX_VALUE)
                .thenComparing(seed -> seed.kind().name());
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
}
