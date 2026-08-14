package io.github.dependencyanalysis.callgraph.protocol.serviceloader;

import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable service contract selected before a strategy creates WALA Context.
 *
 * @param serviceType exact, aggregate, or explicit unknown service
 * @param limitation optional typed coverage limitation
 */
public record ServiceLoaderContractResolution(
        TypeReference serviceType,
        Optional<ModelLimitation> limitation) {

    /** Validates the immutable resolution. */
    public ServiceLoaderContractResolution {
        Objects.requireNonNull(serviceType, "serviceType");
        limitation = Objects.requireNonNull(limitation, "limitation");
    }
}
