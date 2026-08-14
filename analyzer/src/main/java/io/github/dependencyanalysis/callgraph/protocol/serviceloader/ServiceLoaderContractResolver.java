package io.github.dependencyanalysis.callgraph.protocol.serviceloader;

import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import com.ibm.wala.types.TypeReference;


import java.util.Objects;
import java.util.Optional;

/** Pure protocol decision helper shared without WALA execution objects. */
public final class ServiceLoaderContractResolver {

    /** Immutable configured provider facts. */
    private final ServiceLoaderProtocolIndex index;

    /** Stable strategy-specific limitation code. */
    private final String unresolvedCode;

    /**
     * Creates a resolver for one provider index.
     *
     * @param value configured provider facts
     * @param limitationCode unresolved-contract code
     */
    public ServiceLoaderContractResolver(
            final ServiceLoaderProtocolIndex value,
            final String limitationCode) {
        index = Objects.requireNonNull(value, "value");
        unresolvedCode = Objects.requireNonNull(
                limitationCode, "limitationCode");
    }

    /**
     * Resolves a service load contract.
     *
     * @param candidate locally recovered service type
     * @param location stable callsite location
     * @param reportMissingCandidate whether to report a missing local fact
     * @return resolved contract
     */
    public ServiceLoaderContractResolution load(
            final TypeReference candidate,
            final String location,
            final boolean reportMissingCandidate) {
        if (candidate == null) {
            return new ServiceLoaderContractResolution(
                    ServiceLoaderProtocol.unknownService(),
                    reportMissingCandidate
                            ? Optional.of(limitation(location,
                            "non-constant Class argument"))
                            : Optional.empty());
        }
        if (!index.hasProviders(candidate)) {
            return new ServiceLoaderContractResolution(
                    ServiceLoaderProtocol.unknownService(),
                    Optional.of(limitation(location,
                            "missing or unresolved service configuration "
                                    + candidate)));
        }
        return new ServiceLoaderContractResolution(
                candidate, Optional.empty());
    }

    /**
     * Resolves the service associated with an iterator call.
     *
     * @param allocationService allocation-context service type
     * @param localService caller-local service type
     * @param location stable callsite location
     * @return resolved contract
     */
    public ServiceLoaderContractResolution iterator(
            final TypeReference allocationService,
            final TypeReference localService,
            final String location) {
        if (allocationService != null) {
            return new ServiceLoaderContractResolution(
                    allocationService, Optional.empty());
        }
        if (localService == null) {
            return new ServiceLoaderContractResolution(
                    ServiceLoaderProtocol.unknownService(),
                    Optional.of(limitation(location,
                            "iterator receiver contract unavailable")));
        }
        if (!index.hasProviders(localService)) {
            return new ServiceLoaderContractResolution(
                    ServiceLoaderProtocol.unknownService(),
                    Optional.of(limitation(location,
                            "missing or unresolved service configuration "
                                    + localService)));
        }
        return new ServiceLoaderContractResolution(
                localService, Optional.empty());
    }

    private ModelLimitation limitation(
            final String location,
            final String detail) {
        return new ModelLimitation(ModelKind.SERVICE_LOADER,
                unresolvedCode, location, detail);
    }
}
