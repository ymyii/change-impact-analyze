package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundaryMetadata;
import io.github.dependencyanalysis.callgraph.local.LocalConstantResolution;
import io.github.dependencyanalysis.callgraph.local.LocalConstantResolver;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidence;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicModelResolver;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicResolution;
import io.github.dependencyanalysis.callgraph.protocol.methodhandle.LocalMethodHandleFactResolver;
import io.github.dependencyanalysis.callgraph.protocol.methodhandle.LocalMethodHandleResolution;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProtocol;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProviderDefinition;
import io.github.dependencyanalysis.classpath.ClassOwnership;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithmStrategy;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphBuildContext;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyRequest;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphPolicy;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyCapabilities;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyResult;
import io.github.dependencyanalysis.callgraph.strategy.StrategyModelMetadata;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.cha.CHAContextInterpreter;
import com.ibm.wala.ipa.callgraph.cha.ContextInsensitiveCHAContextInterpreter;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Context-insensitive CHA strategy with bounded local protocol constants. */
public final class ChaCallGraphStrategy implements CallGraphAlgorithmStrategy {

    /** Stable program-counter buckets for provider callsites. */
    private static final int SYNTHETIC_PC_BUCKETS = 16384;

    /** High positive program-counter base for protocol callsites. */
    private static final int SYNTHETIC_PC_BASE = 1_000_000_000;

    /** Mask bounding original load PCs in the synthetic range. */
    private static final int LOAD_PC_MASK = 0x7fff;

    @Override
    public CallGraphAlgorithm algorithm() {
        return CallGraphAlgorithm.CHA;
    }

    @Override
    public CallGraphStrategyCapabilities capabilities() {
        return CallGraphStrategyCapabilities.cha();
    }

    @Override
    public CallGraphStrategyResult build(
            final CallGraphBuildContext context,
            final CallGraphStrategyRequest strategyRequest)
            throws Exception {
        if (!(strategyRequest instanceof ChaCallGraphRequest request)) {
            throw new IllegalArgumentException("CHA request required");
        }
        CallGraphPolicy.validate(algorithm(), JdkModelSelection.NONE);
        final ChaContextInterpreter interpreter = new ChaContextInterpreter(
                context, request);
        final ChaDispatchFilteringClassHierarchy filteredHierarchy =
                new ChaDispatchFilteringClassHierarchy(
                        context.hierarchy(), request.dispatchTargets());
        final CHACallGraph graph = new CHACallGraph(
                filteredHierarchy, false);
        graph.setInterpreter(interpreter);
        checkCanceled(context.monitor());
        graph.init(context.entrypoints());
        checkCanceled(context.monitor());
        return new CallGraphStrategyResult(graph,
                new StrategyModelMetadata(
                        new DynamicCallEvidenceIndex(
                                interpreter.dynamicEvidence()),
                        interpreter.limitations(),
                        java.util.Optional.empty(), capabilities(),
                        request.dispatchTargets()
                                .jdkDeclaredDispatchSummary(),
                        java.util.Optional.of(
                                interpreter.boundaryMetadata(graph))));
    }

    private static void checkCanceled(final IProgressMonitor monitor)
            throws CancelException {
        if (monitor.isCanceled()) {
            throw CancelException.make(monitor.getCancelMessage());
        }
    }

    /** CHA callsite interpreter controlling body boundaries and providers. */
    private static final class ChaContextInterpreter
            implements CHAContextInterpreter {

        /** Standard bytecode callsite scanner. */
        private final ContextInsensitiveCHAContextInterpreter base =
                new ContextInsensitiveCHAContextInterpreter();

        /** Common build context. */
        private final CallGraphBuildContext context;

        /** CHA-only build request. */
        private final ChaCallGraphRequest request;

        /** Bounded local service class recovery. */
        private final LocalConstantResolver constants =
                new LocalConstantResolver();

        /** Fixed build limitations. */
        private final Set<ModelLimitation> limitations =
                new LinkedHashSet<>();

        /** Reachable dynamic metadata observations. */
        private final Map<String, DynamicCallEvidence> dynamicEvidence =
                new LinkedHashMap<>();

        ChaContextInterpreter(
                final CallGraphBuildContext buildContext,
                final ChaCallGraphRequest value) {
            context = Objects.requireNonNull(buildContext, "context");
            request = Objects.requireNonNull(value, "request");
            limitations.addAll(context.serviceLoaderIndex().limitations());
        }

        @Override
        public boolean understands(final CGNode node) {
            check();
            return true;
        }

        @Override
        public Iterator<CallSiteReference> iterateCallSites(
                final CGNode node) {
            check();
            if (leaf(node)) {
                return Collections.emptyIterator();
            }
            final List<CallSiteReference> sites = new ArrayList<>();
            base.iterateCallSites(node).forEachRemaining(sites::add);
            inspectDynamicProtocols(node, sites);
            modelServiceLoader(node, sites);
            sites.sort(java.util.Comparator
                    .comparingInt(CallSiteReference::getProgramCounter)
                    .thenComparing(site -> site.getDeclaredTarget()
                            .toString()));
            return sites.iterator();
        }

        @Override
        public Iterator<NewSiteReference> iterateNewSites(
                final CGNode node) {
            check();
            return leaf(node) ? Collections.emptyIterator()
                    : base.iterateNewSites(node);
        }

        private boolean leaf(final CGNode node) {
            final IMethod method = node.getMethod();
            final CodeOrigin origin = origin(method);
            if (origin == CodeOrigin.JDK) {
                return true;
            }
            if (request.dependencyBoundary().noOp(method)) {
                return true;
            }
            return false;
        }

        private CodeOrigin origin(final IMethod method) {
            final ClassOwnership ownership = request.ownership()
                    .ownershipOf(method.getDeclaringClass()
                            .getName().toString());
            if (ownership != null) {
                return ownership.getOrigin();
            }
            final com.ibm.wala.types.ClassLoaderReference loader = method
                    .getDeclaringClass().getClassLoader().getReference();
            return com.ibm.wala.types.ClassLoaderReference.Primordial
                    .equals(loader)
                    || com.ibm.wala.types.ClassLoaderReference.Extension
                    .equals(loader) ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
        }

        private void modelServiceLoader(
                final CGNode caller,
                final List<CallSiteReference> sites) {
            final IR ir = caller.getIR();
            if (ir == null) {
                return;
            }
            final List<CallSiteReference> original = List.copyOf(sites);
            for (CallSiteReference site : original) {
                if (!ServiceLoaderProtocol.singleParameterLoad(
                        site.getDeclaredTarget())) {
                    continue;
                }
                final SSAAbstractInvokeInstruction invoke = invoke(ir, site);
                if (invoke == null || invoke.getNumberOfUses() == 0) {
                    unresolved(caller, site, "invoke-argument-unavailable");
                    continue;
                }
                final LocalConstantResolution resolution = constants.resolve(
                        ir, invoke.getUse(0),
                        LocalConstantResolver.ConstantKind.CLASS);
                if (resolution.status()
                        != LocalConstantResolution.Status.RESOLVED) {
                    unresolved(caller, site, resolution.detail());
                    continue;
                }
                final TypeReference service = resolution.classValue()
                        .orElseThrow();
                final List<ServiceLoaderProviderDefinition> providers =
                        context.serviceLoaderIndex().providers()
                                .getOrDefault(service, List.of());
                for (ServiceLoaderProviderDefinition provider : providers) {
                    final IMethod constructor = provider.constructor();
                    if (!request.dispatchTargets().retains(constructor)) {
                        continue;
                    }
                    final int pc = syntheticPc(
                            site.getProgramCounter(), constructor);
                    final CallSiteReference providerSite =
                            CallSiteReference.make(pc,
                                    constructor.getReference(),
                                    IInvokeInstruction.Dispatch.SPECIAL);
                    sites.add(providerSite);
                }
            }
        }

        private void inspectDynamicProtocols(
                final CGNode caller,
                final List<CallSiteReference> sites) {
            final LocalMethodHandleFactResolver handles =
                    new LocalMethodHandleFactResolver();
            for (CallSiteReference site : List.copyOf(sites)) {
                final InvokeDynamicResolution dynamic =
                        InvokeDynamicModelResolver.inspectForCha(
                                caller, site);
                dynamic.evidence().forEach(value -> dynamicEvidence
                        .putIfAbsent(value.stableKey(), value));
                limitations.addAll(dynamic.limitations());
                final LocalMethodHandleResolution handle = handles.resolve(
                        caller, site);
                if (handle.status()
                        != LocalMethodHandleResolution.Status.NOT_APPLICABLE) {
                    limitations.add(new ModelLimitation(
                            ModelKind.METHOD_HANDLE,
                            "CHA_METHOD_HANDLE_UNSUPPORTED",
                            handle.location(), handle.detail()));
                }
            }
        }

        private SSAAbstractInvokeInstruction invoke(
                final IR ir,
                final CallSiteReference site) {
            if (ir.getCallInstructionIndices(site) == null) {
                return null;
            }
            for (SSAAbstractInvokeInstruction value : ir.getCalls(site)) {
                return value;
            }
            return null;
        }

        private int syntheticPc(
                final int loadPc,
                final IMethod constructor) {
            final int hash = constructor.getReference().toString()
                    .hashCode() & Integer.MAX_VALUE;
            return SYNTHETIC_PC_BASE + (loadPc & LOAD_PC_MASK)
                    * SYNTHETIC_PC_BUCKETS
                    + hash % SYNTHETIC_PC_BUCKETS;
        }

        private void unresolved(
                final CGNode caller,
                final CallSiteReference site,
                final String detail) {
            limitations.add(new ModelLimitation(ModelKind.SERVICE_LOADER,
                    "SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED",
                    caller.getMethod().getReference() + "|pc="
                            + site.getProgramCounter(), detail));
        }

        private void check() {
            if (context.monitor().isCanceled()) {
                throw new ChaCancellationException(
                        context.monitor().getCancelMessage());
            }
        }

        List<ModelLimitation> limitations() {
            return limitations.stream().sorted().toList();
        }

        List<DynamicCallEvidence> dynamicEvidence() {
            return dynamicEvidence.values().stream()
                    .sorted(java.util.Comparator.comparing(
                            DynamicCallEvidence::stableKey)).toList();
        }

        DependencyBodyBoundaryMetadata boundaryMetadata(
                final com.ibm.wala.ipa.callgraph.CallGraph graph) {
            return request.dependencyBoundary().metadata(graph,
                    request.dispatchTargets()
                            .prunedExternalMethodTargetCount());
        }
    }

    /** Unchecked bridge because CHAContextInterpreter cannot throw cancel. */
    private static final class ChaCancellationException
            extends RuntimeException {

        ChaCancellationException(final String message) {
            super(message);
        }
    }
}
