package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrike.shrikeCT.BootstrapMethodsReader.BootstrapMethod;
import com.ibm.wala.shrike.shrikeCT.ClassConstants;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

/** Stateless decoder/helper shared by strategy-specific decorators. */
final class InvokeDynamicModelResolver {

    /** Default Java lambda bootstrap. */
    private static final InvokeDynamicBootstrapKey METAFACTORY =
            new InvokeDynamicBootstrapKey(
                    BootstrapMethod.LAMBDA_METAFACTORY_CLASS,
                    BootstrapMethod.BOOTSTRAP_METHOD_NAME,
                    BootstrapMethod.BOOTSTRAP_METHOD_TYPE);

    private InvokeDynamicModelResolver() {
    }

    static InvokeDynamicResolution resolve(
            final CGNode caller,
            final CallSiteReference site,
            final InvokeDynamicBootstrapModelRegistry registry) {
        final SSAInvokeDynamicInstruction instruction = instruction(
                caller, site);
        if (instruction == null) {
            return new InvokeDynamicResolution(
                    InvokeDynamicResolution.Disposition.DELEGATE,
                    Optional.empty(), List.of(), List.of());
        }
        final BootstrapMethod bootstrap = instruction.getBootstrap();
        final InvokeDynamicBootstrapKey key = new InvokeDynamicBootstrapKey(
                bootstrap.methodClass(), bootstrap.methodName(),
                bootstrap.methodType());
        final CapturedMetadata captured = capture(caller, site, bootstrap);
        if (METAFACTORY.equals(key)) {
            return new InvokeDynamicResolution(
                    InvokeDynamicResolution.Disposition.DELEGATE,
                    Optional.empty(), captured.evidence(),
                    captured.limitations());
        }
        final InvokeDynamicBootstrapModel model = registry.find(key)
                .orElse(null);
        if (model == null) {
            final ArrayList<ModelLimitation> limitations =
                    new ArrayList<>(captured.limitations());
            limitations.add(limitation(
                    "INVOKEDYNAMIC_BOOTSTRAP_UNSUPPORTED",
                    caller, site, "bootstrap=" + key));
            return new InvokeDynamicResolution(
                    InvokeDynamicResolution.Disposition.DELEGATE,
                    Optional.empty(), captured.evidence(), limitations);
        }
        final InvokeDynamicModelResult result = model.model(
                caller, site, instruction, caller.getClassHierarchy(),
                caller.getClassHierarchy()::addClass);
        if (result.target().isPresent()) {
            return new InvokeDynamicResolution(
                    InvokeDynamicResolution.Disposition.TARGET,
                    result.target(), captured.evidence(),
                    captured.limitations());
        }
        final ArrayList<ModelLimitation> limitations =
                new ArrayList<>(captured.limitations());
        limitations.add(limitation(
                "INVOKEDYNAMIC_MODEL_UNSUPPORTED", caller, site,
                "bootstrap=" + key + "; reason="
                        + result.limitation().orElse("unspecified")));
        return new InvokeDynamicResolution(
                InvokeDynamicResolution.Disposition.NO_TARGET,
                Optional.empty(), captured.evidence(), limitations);
    }

    private static SSAInvokeDynamicInstruction instruction(
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

    private static CapturedMetadata capture(
            final CGNode caller,
            final CallSiteReference site,
            final BootstrapMethod bootstrap) {
        final ArrayList<DynamicCallEvidence> evidence = new ArrayList<>();
        final ArrayList<ModelLimitation> limitations = new ArrayList<>();
        evidence.add(new DynamicCallEvidence(
                bootstrap.methodClass(), bootstrap.methodName(),
                bootstrap.methodType(), caller, site.getProgramCounter(),
                EdgeKind.INVOKEDYNAMIC_BOOTSTRAP,
                DynamicReferenceKind.BOOTSTRAP_IMPLEMENTATION_METHOD,
                Optional.empty(), "bootstrap=" + bootstrap));
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
                final String owner = bootstrap.getCP()
                        .getCPHandleClass(cpIndex);
                final String name = bootstrap.getCP()
                        .getCPHandleName(cpIndex);
                final MethodHandleReferenceKind referenceKind =
                        MethodHandleReferenceKind.fromClassFileKind(
                                bootstrap.getCP().getCPHandleKind(cpIndex));
                evidence.add(new DynamicCallEvidence(
                        owner, name, descriptor, caller,
                        site.getProgramCounter(),
                        EdgeKind.INVOKEDYNAMIC_HANDLE_REFERENCE,
                        DynamicReferenceKind
                                .BOOTSTRAP_ARGUMENT_METHOD_HANDLE,
                        Optional.of(referenceKind),
                        "bootstrap=" + bootstrap + "; argument=" + index
                                + "; handleKind=" + referenceKind
                                + "; handle=" + owner + "#" + name
                                + descriptor));
            } catch (InvalidClassFileException exception) {
                limitations.add(limitation(
                        "INVOKEDYNAMIC_BOOTSTRAP_ARGUMENT_INVALID",
                        caller, site, "argument=" + index));
            }
        }
        return new CapturedMetadata(evidence, limitations);
    }

    private static ModelLimitation limitation(
            final String code,
            final CGNode caller,
            final CallSiteReference site,
            final String detail) {
        return new ModelLimitation(ModelKind.INVOKEDYNAMIC, code,
                ModuleAnalysisReason.INCONCLUSIVE_INVOKEDYNAMIC_MODEL,
                caller.getMethod().getReference() + "|pc="
                        + site.getProgramCounter(), detail);
    }

    /**
     * Decoded metadata before selector disposition is selected.
     *
     * @param evidence typed evidence
     * @param limitations typed limitations
     */
    private record CapturedMetadata(
            List<DynamicCallEvidence> evidence,
            List<ModelLimitation> limitations) {

        CapturedMetadata {
            evidence = List.copyOf(evidence);
            limitations = List.copyOf(limitations);
        }
    }
}
