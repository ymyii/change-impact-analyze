package io.github.dependencyanalysis.callgraph.protocol.methodhandle;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves supported MethodHandle targets from one reachable caller IR. */
public final class LocalMethodHandleFactResolver {

    /** Lookup receiver, class and name uses required by findStatic. */
    private static final int MIN_FIND_STATIC_USES = 3;

    /** MethodHandle internal owner. */
    private static final String METHOD_HANDLE =
            "java/lang/invoke/MethodHandle";

    /** MethodHandles.Lookup internal owner. */
    private static final String LOOKUP =
            "java/lang/invoke/MethodHandles$Lookup";

    /**
     * Resolves one callsite from its caller IR.
     *
     * @param caller reachable caller
     * @param site source callsite
     * @return local resolution
     */
    public LocalMethodHandleResolution resolve(
            final CGNode caller,
            final CallSiteReference site) {
        return resolve(caller, caller.getIR(), site);
    }

    /**
     * Resolves one callsite from an explicit caller IR.
     *
     * @param caller reachable caller
     * @param ir caller intermediate representation
     * @param site source callsite
     * @return local resolution
     */
    public LocalMethodHandleResolution resolve(
            final CGNode caller,
            final IR ir,
            final CallSiteReference site) {
        final MethodReference declared = site.getDeclaredTarget();
        if (!METHOD_HANDLE.equals(owner(declared.getDeclaringClass()))) {
            return LocalMethodHandleResolution.notApplicable(caller, site);
        }
        final LocalMethodHandleResolution.Operation operation =
                operation(declared);
        if (operation == LocalMethodHandleResolution.Operation.NONE) {
            return LocalMethodHandleResolution.notApplicable(caller, site);
        }
        final SSAAbstractInvokeInstruction invoke = invoke(ir, site);
        if (invoke == null || invoke.getNumberOfUses() == 0) {
            return LocalMethodHandleResolution.unsupported(
                    operation, caller, site,
                    "invoke instruction unavailable");
        }
        final FindStaticFact fact = findStaticFact(
                ir, invoke.getReceiver(), new HashSet<>());
        if (fact == null) {
            return LocalMethodHandleResolution.unsupported(
                    operation, caller, site,
                    "receiver does not resolve to one local findStatic");
        }
        final IClass ownerClass = caller.getClassHierarchy().lookupClass(
                fact.owner());
        if (ownerClass == null) {
            return LocalMethodHandleResolution.unsupported(
                    operation, caller, site,
                    "target owner unresolved: " + fact.owner());
        }
        final List<IMethod> candidates = new ArrayList<>();
        for (IMethod method : ownerClass.getAllMethods()) {
            if (method.isStatic()
                    && fact.name().equals(method.getName().toString())
                    && matchesDescriptor(declared, method)) {
                candidates.add(method);
            }
        }
        candidates.sort(Comparator.comparing(
                method -> method.getReference().toString()));
        if (candidates.size() != 1) {
            return LocalMethodHandleResolution.unsupported(
                    operation, caller, site,
                    "target candidate count=" + candidates.size()
                            + "; owner=" + fact.owner()
                            + "; name=" + fact.name());
        }
        return LocalMethodHandleResolution.modeled(
                operation, candidates.get(0), caller, site);
    }

    private LocalMethodHandleResolution.Operation operation(
            final MethodReference method) {
        final String name = method.getName().toString();
        if ("invokeExact".equals(name)) {
            return LocalMethodHandleResolution.Operation.INVOKE_EXACT;
        }
        if ("invokeWithArguments".equals(name)) {
            return LocalMethodHandleResolution.Operation
                    .INVOKE_WITH_ARGUMENTS;
        }
        return LocalMethodHandleResolution.Operation.NONE;
    }

    private boolean matchesDescriptor(
            final MethodReference invocation,
            final IMethod target) {
        return "invokeWithArguments".equals(
                invocation.getName().toString())
                || invocation.getDescriptor().equals(target.getDescriptor());
    }

    private FindStaticFact findStaticFact(
            final IR ir,
            final int value,
            final Set<Integer> visiting) {
        if (!visiting.add(value)) {
            return null;
        }
        final SSAInstruction definition = new DefUse(ir).getDef(value);
        if (definition instanceof SSAAbstractInvokeInstruction invoke
                && findStatic(invoke.getDeclaredTarget())) {
            return findStaticFact(ir, invoke);
        }
        if (definition instanceof SSACheckCastInstruction cast) {
            return findStaticFact(ir, cast.getVal(), visiting);
        }
        if (definition instanceof SSAPiInstruction pi) {
            return findStaticFact(ir, pi.getVal(), visiting);
        }
        if (definition instanceof SSAPhiInstruction phi) {
            FindStaticFact result = null;
            for (int index = 0; index < phi.getNumberOfUses(); index++) {
                final FindStaticFact candidate = findStaticFact(
                        ir, phi.getUse(index), new HashSet<>(visiting));
                if (candidate == null) {
                    return null;
                }
                if (result != null && !result.equals(candidate)) {
                    return null;
                }
                result = candidate;
            }
            return result;
        }
        return null;
    }

    private FindStaticFact findStaticFact(
            final IR ir,
            final SSAAbstractInvokeInstruction invoke) {
        if (invoke.getNumberOfUses() < MIN_FIND_STATIC_USES) {
            return null;
        }
        final TypeReference owner = classConstant(ir, invoke.getUse(1));
        final String name = stringConstant(ir, invoke.getUse(2));
        return owner == null || name == null
                ? null : new FindStaticFact(owner, name);
    }

    private TypeReference classConstant(final IR ir, final int value) {
        if (ir.getSymbolTable().isConstant(value)) {
            return type(ir.getSymbolTable().getConstantValue(value));
        }
        final SSAInstruction definition = new DefUse(ir).getDef(value);
        return definition instanceof SSALoadMetadataInstruction metadata
                ? type(metadata.getToken()) : null;
    }

    private TypeReference type(final Object value) {
        if (value instanceof IClass type) {
            return type.getReference();
        }
        return value instanceof TypeReference type ? type : null;
    }

    private String stringConstant(final IR ir, final int value) {
        return ir.getSymbolTable().isStringConstant(value)
                ? ir.getSymbolTable().getStringValue(value) : null;
    }

    private SSAAbstractInvokeInstruction invoke(
            final IR ir,
            final CallSiteReference site) {
        if (ir == null || ir.getCallInstructionIndices(site) == null) {
            return null;
        }
        for (SSAAbstractInvokeInstruction invoke : ir.getCalls(site)) {
            return invoke;
        }
        return null;
    }

    private boolean findStatic(final MethodReference method) {
        return LOOKUP.equals(owner(method.getDeclaringClass()))
                && method.getName().toString().startsWith("findStatic");
    }

    private String owner(final TypeReference type) {
        final String value = Objects.requireNonNull(type, "type")
                .getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    /**
     * Exact local findStatic constants.
     *
     * @param owner target owner
     * @param name target method name
     */
    private record FindStaticFact(TypeReference owner, String name) {
    }
}
