package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** RTA-specific ServiceLoader model installation. */
final class RtaServiceLoaderInstaller {

    /** Per-build model state. */
    private final RtaServiceLoaderModel model;

    /** RTA-local Context execution. */
    private final RtaContextSelector selector;

    /** RTA-local summary interpreter and cache. */
    private final RtaServiceLoaderContextInterpreter interpreter;

    RtaServiceLoaderInstaller(
            final ServiceLoaderProtocolIndex index,
            final IClassHierarchy hierarchy,
            final ContextSelector next) {
        model = new RtaServiceLoaderModel(index, hierarchy);
        selector = new RtaContextSelector(next, index);
        interpreter = new RtaServiceLoaderContextInterpreter(model);
    }

    ContextSelector contextSelector() {
        return selector;
    }

    SSAContextInterpreter contextInterpreter() {
        return interpreter;
    }

    List<ModelLimitation> limitations() {
        final Set<ModelLimitation> result = new LinkedHashSet<>(
                model.limitations());
        result.addAll(selector.limitations());
        return result.stream().sorted().toList();
    }

    ServiceLoaderModelMetadata metadata(final CallGraph graph) {
        return ServiceLoaderModelMetadata.snapshot(
                graph, model, limitations());
    }

    /** RTA caller-local contract selector without points-to behavior. */
    private static final class RtaContextSelector
            implements ContextSelector {

        /** Parameter relevant to ServiceLoader protocol calls. */
        private static final IntSet FIRST_PARAMETER =
                IntSetUtil.make(new int[]{0});

        /** Previously assembled RTA ContextSelector. */
        private final ContextSelector base;

        /** Pure provider-contract decision helper. */
        private final ServiceLoaderContractResolver contracts;

        /** Caller-local RTA IR resolver. */
        private final RtaServiceContractResolver local =
                new RtaServiceContractResolver();

        /** RTA-local fixed-point limitations. */
        private final Set<ModelLimitation> limitations =
                new LinkedHashSet<>();

        RtaContextSelector(
                final ContextSelector next,
                final ServiceLoaderProtocolIndex index) {
            base = Objects.requireNonNull(next, "next");
            contracts = new ServiceLoaderContractResolver(index,
                    "RTA_SERVICE_LOADER_CONTRACT_UNRESOLVED");
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
            final Context safeBase = Everywhere.EVERYWHERE;
            final String location = caller.getMethod().getReference()
                    + "|pc=" + site.getProgramCounter();
            if (ServiceLoaderProtocol.loadMethod(
                    callee.getReference())) {
                final ServiceLoaderContractResolution resolution =
                        contracts.load(local.loadServiceType(caller, site),
                                location, false);
                resolution.limitation().ifPresent(limitations::add);
                return new RtaServiceContext(safeBase,
                        resolution.serviceType(), new RtaLoadSiteIdentity(
                        caller.getMethod().getReference().toString(),
                        site.getProgramCounter(), callee.getName().toString()));
            }
            if (!ServiceLoaderProtocol.iteratorMethod(
                    callee.getReference())) {
                return delegated;
            }
            final ServiceLoaderContractResolution resolution =
                    contracts.iterator(null,
                            local.iteratorServiceType(caller, site), location);
            resolution.limitation().ifPresent(limitations::add);
            return new RtaServiceContext(safeBase,
                    resolution.serviceType(), new RtaLoadSiteIdentity(
                    caller.getMethod().getReference().toString(),
                    site.getProgramCounter(), "iterator"));
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
            return result;
        }

        List<ModelLimitation> limitations() {
            return limitations.stream().sorted().toList();
        }
    }

    /** RTA-owned service Context. */
    private static final class RtaServiceContext implements Context {

        /** Exact service identity key. */
        private static final ContextKey SERVICE_KEY = new ContextKey() {
            @Override
            public String toString() {
                return "RTA_SERVICE_LOADER_TYPE";
            }
        };

        /** Stable load-site identity key. */
        private static final ContextKey LOAD_SITE_KEY = new ContextKey() {
            @Override
            public String toString() {
                return "RTA_SERVICE_LOADER_LOAD_SITE";
            }
        };

        /** Previously selected RTA Context. */
        private final Context base;

        /** Exact or explicit unknown service. */
        private final TypeReference serviceType;

        /** Caller binary identity plus PC. */
        private final ContextItem loadSite;

        RtaServiceContext(
                final Context next,
                final TypeReference service,
                final ContextItem identity) {
            base = Objects.requireNonNull(next, "next");
            serviceType = Objects.requireNonNull(service, "service");
            loadSite = Objects.requireNonNull(identity, "identity");
        }

        @Override
        public ContextItem get(final ContextKey name) {
            if (SERVICE_KEY.equals(name)
                    || RtaServiceLoaderModel.SERVICE_TYPE_KEY.equals(name)) {
                return RtaServiceLoaderModel.serviceTypeItem(
                        serviceType);
            }
            return LOAD_SITE_KEY.equals(name) ? loadSite : base.get(name);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof RtaServiceContext that
                    && base.equals(that.base)
                    && serviceType.equals(that.serviceType)
                    && loadSite.equals(that.loadSite);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base, serviceType, loadSite);
        }

        @Override
        public String toString() {
            return "rta-service-loader:" + serviceType + ":"
                    + loadSite + ":" + base;
        }
    }

    /**
     * @param callerMethod stable caller binary identity
     * @param bytecodePc call-site PC
     * @param operation ServiceLoader operation
     */
    private record RtaLoadSiteIdentity(
            String callerMethod,
            int bytecodePc,
            String operation) implements ContextItem {
    }
}
