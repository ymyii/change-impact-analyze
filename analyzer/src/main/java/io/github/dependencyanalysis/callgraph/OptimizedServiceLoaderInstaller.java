package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa
        .DelegatingSSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Optimized 0-1-CFA allocation-sensitive ServiceLoader installation. */
final class OptimizedServiceLoaderInstaller {

    /** Per-build model state. */
    private final OptimizedServiceLoaderModel model;

    /** Immutable provider facts used by the optimized selector. */
    private final ServiceLoaderProtocolIndex protocolIndex;

    /** Optimized-local Context execution. */
    private OptimizedContextSelector selector;

    /** Optimized-local summary execution. */
    private OptimizedServiceLoaderContextInterpreter interpreter;

    OptimizedServiceLoaderInstaller(
            final ServiceLoaderProtocolIndex index,
            final IClassHierarchy hierarchy) {
        protocolIndex = Objects.requireNonNull(index, "index");
        model = new OptimizedServiceLoaderModel(protocolIndex, hierarchy);
    }

    void install(final SSAPropagationCallGraphBuilder builder) {
        selector = new OptimizedContextSelector(
                builder.getContextSelector(), protocolIndex);
        interpreter = new OptimizedServiceLoaderContextInterpreter(model);
        builder.setContextSelector(selector);
        builder.setContextInterpreter(
                new DelegatingSSAContextInterpreter(
                        interpreter,
                        builder.getCFAContextInterpreter()));
    }

    List<ModelLimitation> limitations() {
        final Set<ModelLimitation> result = new LinkedHashSet<>(
                model.limitations());
        if (selector != null) {
            result.addAll(selector.limitations());
        }
        return result.stream().sorted().toList();
    }

    ServiceLoaderModelMetadata metadata(final CallGraph graph) {
        return ServiceLoaderModelMetadata.snapshot(
                graph, model, limitations());
    }

    /** Optimized allocation-site contract propagation. */
    private static final class OptimizedContextSelector
            implements ContextSelector {

        /** First call parameter or receiver. */
        private static final IntSet FIRST_PARAMETER =
                IntSetUtil.make(new int[]{0});

        /** Previously installed optimized selector. */
        private final ContextSelector base;

        /** Pure provider-contract decision helper. */
        private final ServiceLoaderContractResolver contracts;

        /** Optimized-local fixed-point limitations. */
        private final Set<ModelLimitation> limitations =
                new LinkedHashSet<>();

        OptimizedContextSelector(
                final ContextSelector next,
                final ServiceLoaderProtocolIndex index) {
            base = Objects.requireNonNull(next, "next");
            contracts = new ServiceLoaderContractResolver(index,
                    "SERVICE_LOADER_CONTRACT_UNRESOLVED");
        }

        @Override
        public Context getCalleeTarget(
                final CGNode caller,
                final CallSiteReference site,
                final IMethod callee,
                final InstanceKey[] actualParameters) {
            final Context delegated = base.getCalleeTarget(
                    caller, site, callee, actualParameters);
            if (!ClassLoaderReference.Application.equals(caller.getMethod()
                    .getDeclaringClass().getClassLoader().getReference())) {
                return delegated;
            }
            final Context safeBase = delegated == null
                    ? Everywhere.EVERYWHERE : delegated;
            final String location = caller.getMethod().getReference()
                    + "|pc=" + site.getProgramCounter();
            if (ServiceLoaderProtocol.loadMethod(
                    callee.getReference())) {
                final ServiceLoaderContractResolution resolution =
                        contracts.load(constantServiceType(actualParameters),
                                location, true);
                resolution.limitation().ifPresent(limitations::add);
                return new OptimizedServiceContext(safeBase,
                        resolution.serviceType(), loaderIdentity(
                        caller, site, callee, actualParameters));
            }
            if (!ServiceLoaderProtocol.iteratorMethod(
                    callee.getReference())) {
                return delegated;
            }
            final OptimizedServiceContext allocation = allocationContext(
                    actualParameters);
            final ServiceLoaderContractResolution resolution =
                    contracts.iterator(allocation == null ? null
                            : allocation.serviceType(), null, location);
            resolution.limitation().ifPresent(limitations::add);
            return new OptimizedServiceContext(safeBase,
                    resolution.serviceType(), allocation == null
                    ? new OptimizedLoadSiteIdentity(
                            caller.getMethod().getReference().toString(),
                            site.getProgramCounter(), "iterator")
                    : allocation.loaderIdentity());
        }

        @Override
        public IntSet getRelevantParameters(
                final CGNode caller,
                final CallSiteReference site) {
            final MutableIntSet result = IntSetUtil.makeMutableCopy(
                    base.getRelevantParameters(caller, site));
            if (ServiceLoaderProtocol.loadMethod(
                    site.getDeclaredTarget())
                    || ServiceLoaderProtocol.iteratorMethod(
                    site.getDeclaredTarget())) {
                result.addAll(FIRST_PARAMETER);
            }
            if (ServiceLoaderProtocol.twoParameterLoad(
                    site.getDeclaredTarget())) {
                result.add(1);
            }
            return result;
        }

        private TypeReference constantServiceType(
                final InstanceKey[] parameters) {
            if (parameters == null || parameters.length == 0
                    || !(parameters[0] instanceof ConstantKey<?> key)) {
                return null;
            }
            if (key.getValue() instanceof IClass type) {
                return type.getReference();
            }
            return key.getValue() instanceof TypeReference type ? type : null;
        }

        private OptimizedServiceContext allocationContext(
                final InstanceKey[] parameters) {
            if (parameters == null || parameters.length == 0
                    || !(parameters[0]
                    instanceof AllocationSiteInNode allocation)
                    || !(allocation.getNode().getContext()
                    instanceof OptimizedServiceContext context)) {
                return null;
            }
            return context;
        }

        private ContextItem loaderIdentity(
                final CGNode caller,
                final CallSiteReference site,
                final IMethod callee,
                final InstanceKey[] parameters) {
            if (ServiceLoaderProtocol.twoParameterLoad(
                    callee.getReference()) && parameters != null
                    && parameters.length > 1 && parameters[1] != null) {
                return parameters[1];
            }
            return new OptimizedLoadSiteIdentity(
                    caller.getMethod().getReference().toString(),
                    site.getProgramCounter(), callee.getName().toString());
        }

        List<ModelLimitation> limitations() {
            return limitations.stream().sorted().toList();
        }
    }

    /** Optimized 0-1-CFA-owned service Context. */
    private static final class OptimizedServiceContext
            implements Context {

        /** Exact service identity key. */
        private static final ContextKey SERVICE_KEY = new ContextKey() {
            @Override
            public String toString() {
                return "OPTIMIZED_SERVICE_LOADER_TYPE";
            }
        };

        /** Allocation/loader identity key. */
        private static final ContextKey LOADER_KEY = new ContextKey() {
            @Override
            public String toString() {
                return "OPTIMIZED_SERVICE_LOADER_IDENTITY";
            }
        };

        /** Previously selected optimized Context. */
        private final Context base;

        /** Exact or explicit unknown service. */
        private final TypeReference serviceType;

        /** Explicit loader or stable load site. */
        private final ContextItem loaderIdentity;

        OptimizedServiceContext(
                final Context next,
                final TypeReference service,
                final ContextItem identity) {
            base = Objects.requireNonNull(next, "next");
            serviceType = Objects.requireNonNull(service, "service");
            loaderIdentity = Objects.requireNonNull(identity, "identity");
        }

        @Override
        public ContextItem get(final ContextKey name) {
            if (SERVICE_KEY.equals(name)
                    || OptimizedServiceLoaderModel.SERVICE_TYPE_KEY
                    .equals(name)) {
                return OptimizedServiceLoaderModel.serviceTypeItem(
                        serviceType);
            }
            return LOADER_KEY.equals(name)
                    ? loaderIdentity : base.get(name);
        }

        TypeReference serviceType() {
            return serviceType;
        }

        ContextItem loaderIdentity() {
            return loaderIdentity;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof OptimizedServiceContext that
                    && base.equals(that.base)
                    && serviceType.equals(that.serviceType)
                    && loaderIdentity.equals(that.loaderIdentity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base, serviceType, loaderIdentity);
        }

        @Override
        public String toString() {
            return "optimized-service-loader:" + serviceType + ":"
                    + loaderIdentity + ":" + base;
        }
    }

    /**
     * @param callerMethod stable caller binary identity
     * @param bytecodePc call-site PC
     * @param operation ServiceLoader operation
     */
    private record OptimizedLoadSiteIdentity(
            String callerMethod,
            int bytecodePc,
            String operation) implements ContextItem {
    }
}
