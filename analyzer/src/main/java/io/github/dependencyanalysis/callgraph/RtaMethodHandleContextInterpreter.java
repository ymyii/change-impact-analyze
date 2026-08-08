package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Adds caller-local RTA MethodHandle bridge callsites. */
final class RtaMethodHandleContextInterpreter
        implements SSAContextInterpreter {

    /** Default IR interpreter for ordinary application methods. */
    private final SSAContextInterpreter base;

    /** Caller-local fact resolver. */
    private final RtaMethodHandleFactResolver resolver;

    /** Stable bridge summary factory. */
    private final RtaMethodHandleSummaryFactory summaries;

    /** Build-time typed metadata. */
    private final RtaMethodHandleModelState state;

    /** Additional bridge callsites by reachable caller. */
    private final Map<CGNode, List<CallSiteReference>> calls =
            new HashMap<>();

    RtaMethodHandleContextInterpreter(
            final SSAContextInterpreter delegate,
            final RtaMethodHandleFactResolver factResolver,
            final RtaMethodHandleSummaryFactory summaryFactory,
            final RtaMethodHandleModelState modelState) {
        base = Objects.requireNonNull(delegate, "delegate");
        resolver = Objects.requireNonNull(factResolver, "factResolver");
        summaries = Objects.requireNonNull(summaryFactory, "summaryFactory");
        state = Objects.requireNonNull(modelState, "modelState");
    }

    @Override
    public boolean understands(final CGNode node) {
        return ClassLoaderReference.Application.equals(node.getMethod()
                .getDeclaringClass().getClassLoader().getReference())
                && !modeledCalls(node).isEmpty();
    }

    private List<CallSiteReference> modeledCalls(final CGNode node) {
        return calls.computeIfAbsent(node, this::resolveCalls);
    }

    private List<CallSiteReference> resolveCalls(final CGNode node) {
        final List<CallSiteReference> result = new ArrayList<>();
        final IR ir = base.getIR(node);
        final Iterator<CallSiteReference> sites = base.iterateCallSites(node);
        while (sites.hasNext()) {
            final CallSiteReference site = sites.next();
            final LocalMethodHandleResolution resolution = resolver.resolve(
                    node, ir, site);
            if (resolution.status()
                    == LocalMethodHandleResolution.Status.NOT_APPLICABLE) {
                continue;
            }
            if (resolution.status()
                    == LocalMethodHandleResolution.Status.UNSUPPORTED) {
                state.addLimitation(
                        resolution.location(), resolution.detail());
                continue;
            }
            final IMethod bridge = summaries.bridge(
                    resolution, site);
            result.add(CallSiteReference.make(site.getProgramCounter(),
                    bridge.getReference(), Dispatch.STATIC));
            recordEvidence(resolution, node);
        }
        return List.copyOf(result);
    }

    private void recordEvidence(
            final LocalMethodHandleResolution resolution,
            final CGNode caller) {
        final IMethod target = resolution.targetMethod().orElseThrow();
        state.addEvidence(new DynamicCallEvidence(
                resolution.targetOwner(), resolution.targetName(),
                resolution.targetDescriptor(), caller,
                resolution.bytecodePc(), EdgeKind.METHOD_HANDLE_TARGET,
                DynamicReferenceKind.DIRECT_MODELED_HANDLE_TARGET,
                Optional.of(MethodHandleReferenceKind.REF_INVOKE_STATIC),
                "RTA_METHOD_HANDLE_LOCAL_TARGET|operation="
                        + resolution.operation() + "; target="
                        + target.getReference()));
    }

    @Override
    public IR getIR(final CGNode node) {
        return base.getIR(node);
    }

    @Override
    public Iterator<NewSiteReference> iterateNewSites(final CGNode node) {
        return base.iterateNewSites(node);
    }

    @Override
    public Iterator<FieldReference> iterateFieldsRead(final CGNode node) {
        return base.iterateFieldsRead(node);
    }

    @Override
    public Iterator<FieldReference> iterateFieldsWritten(final CGNode node) {
        return base.iterateFieldsWritten(node);
    }

    @Override
    public boolean recordFactoryType(
            final CGNode node, final IClass klass) {
        return base.recordFactoryType(node, klass);
    }

    @Override
    public Iterator<CallSiteReference> iterateCallSites(final CGNode node) {
        final List<CallSiteReference> result = new ArrayList<>();
        base.iterateCallSites(node).forEachRemaining(result::add);
        result.addAll(modeledCalls(node));
        return result.iterator();
    }

    @Override
    public IRView getIRView(final CGNode node) {
        return base.getIRView(node);
    }

    @Override
    public DefUse getDU(final CGNode node) {
        return base.getDU(node);
    }

    @Override
    public int getNumberOfStatements(final CGNode node) {
        return base.getNumberOfStatements(node);
    }

    @Override
    public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(
            final CGNode node) {
        return base.getCFG(node);
    }
}
