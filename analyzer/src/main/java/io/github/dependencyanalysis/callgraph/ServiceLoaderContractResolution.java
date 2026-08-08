package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.types.TypeReference;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable service contract selected before a strategy creates WALA Context.
 *
 * @param serviceType exact, aggregate, or explicit unknown service
 * @param limitation optional typed coverage limitation
 */
record ServiceLoaderContractResolution(
        TypeReference serviceType,
        Optional<ModelLimitation> limitation) {

    ServiceLoaderContractResolution {
        Objects.requireNonNull(serviceType, "serviceType");
        limitation = Objects.requireNonNull(limitation, "limitation");
    }
}
