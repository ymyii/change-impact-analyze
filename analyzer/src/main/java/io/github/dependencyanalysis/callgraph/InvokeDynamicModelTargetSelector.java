package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.shrike.shrikeCT.BootstrapMethodsReader.BootstrapMethod;
import com.ibm.wala.shrike.shrikeCT.ClassConstants;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;

import java.util.Objects;

/** Outer selector capturing evidence and dispatching exact bootstrap models. */
final class InvokeDynamicModelTargetSelector
        implements MethodTargetSelector {

    /** Default Java lambda bootstrap. */
    private static final InvokeDynamicBootstrapKey METAFACTORY =
            new InvokeDynamicBootstrapKey(
                    BootstrapMethod.LAMBDA_METAFACTORY_CLASS,
                    BootstrapMethod.BOOTSTRAP_METHOD_NAME,
                    BootstrapMethod.BOOTSTRAP_METHOD_TYPE);

    /** Previously installed WALA selector chain. */
    private final MethodTargetSelector base;

    /** Exact model registry. */
    private final InvokeDynamicBootstrapModelRegistry registry;

    /** Build-time evidence and limitations. */
    private final InvokeDynamicModelState state;

    InvokeDynamicModelTargetSelector(
            final MethodTargetSelector delegate,
            final InvokeDynamicBootstrapModelRegistry models,
            final InvokeDynamicModelState modelState) {
        base = Objects.requireNonNull(delegate, "delegate");
        registry = Objects.requireNonNull(models, "models");
        state = Objects.requireNonNull(modelState, "modelState");
    }

    @Override
    public IMethod getCalleeTarget(
            final CGNode caller,
            final CallSiteReference site,
            final IClass receiver) {
        final SSAInvokeDynamicInstruction instruction = instruction(
                caller, site);
        if (instruction == null) {
            return base.getCalleeTarget(caller, site, receiver);
        }
        final BootstrapMethod bootstrap = instruction.getBootstrap();
        final InvokeDynamicBootstrapKey key = new InvokeDynamicBootstrapKey(
                bootstrap.methodClass(), bootstrap.methodName(),
                bootstrap.methodType());
        capture(caller, site, bootstrap);
        if (METAFACTORY.equals(key)) {
            return base.getCalleeTarget(caller, site, receiver);
        }
        final InvokeDynamicBootstrapModel model = registry.find(key)
                .orElse(null);
        if (model == null) {
            state.addLimitation("Reachable unsupported invokedynamic "
                    + "bootstrap: caller="
                    + caller.getMethod().getReference() + "; pc="
                    + site.getProgramCounter() + "; bootstrap=" + key);
            return base.getCalleeTarget(caller, site, receiver);
        }
        final InvokeDynamicModelResult result = model.model(
                caller, site, instruction, caller.getClassHierarchy(),
                caller.getClassHierarchy()::addClass);
        if (result.target().isPresent()) {
            return result.target().orElseThrow();
        }
        state.addLimitation("Reachable unsupported invokedynamic model: "
                + "caller=" + caller.getMethod().getReference()
                + "; pc=" + site.getProgramCounter() + "; bootstrap="
                + key + "; reason="
                + result.limitation().orElse("unspecified"));
        return null;
    }

    private SSAInvokeDynamicInstruction instruction(
            final CGNode caller,
            final CallSiteReference site) {
        final IR ir = caller.getIR();
        if (ir == null || ir.getCallInstructionIndices(site) == null) {
            return null;
        }
        for (SSAAbstractInvokeInstruction call : ir.getCalls(site)) {
            if (call instanceof SSAInvokeDynamicInstruction dynamic) {
                return dynamic;
            }
        }
        return null;
    }

    private void capture(
            final CGNode caller,
            final CallSiteReference site,
            final BootstrapMethod bootstrap) {
        state.addEvidence(new DynamicCallEvidence(
                bootstrap.methodClass(), bootstrap.methodName(),
                bootstrap.methodType(), caller, site.getProgramCounter(),
                EdgeKind.INVOKEDYNAMIC_BOOTSTRAP,
                "bootstrap=" + bootstrap));
        for (int index = 0;
                index < bootstrap.callArgumentCount(); index++) {
            try {
                if (bootstrap.callArgumentKind(index)
                        != ClassConstants.CONSTANT_MethodHandle) {
                    continue;
                }
                final int cpIndex = bootstrap.callArgumentIndex(index);
                final String descriptor = bootstrap.getCP()
                        .getCPHandleType(cpIndex);
                if (!descriptor.startsWith("(")) {
                    continue;
                }
                final String owner = bootstrap.getCP()
                        .getCPHandleClass(cpIndex);
                final String name = bootstrap.getCP()
                        .getCPHandleName(cpIndex);
                final int kind = bootstrap.getCP()
                        .getCPHandleKind(cpIndex);
                state.addEvidence(new DynamicCallEvidence(
                        owner, name, descriptor, caller,
                        site.getProgramCounter(),
                        EdgeKind.INVOKEDYNAMIC_HANDLE_REFERENCE,
                        "bootstrap=" + bootstrap + "; argument=" + index
                                + "; handleKind=" + kind + "; handle="
                                + owner + "#" + name + descriptor));
            } catch (InvalidClassFileException exception) {
                state.addLimitation(
                        "Invalid reachable invokedynamic bootstrap argument: "
                                + caller.getMethod().getReference() + "; pc="
                                + site.getProgramCounter() + "; argument="
                                + index);
            }
        }
    }
}
