package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;

/**
 * ZeroCFA constant receiver identity for one modeled service allocation.
 *
 * @param serviceType requested service
 * @param loaderIdentity explicit loader or stable load site
 */
record ZeroCfaServiceLoaderIdentity(
        TypeReference serviceType,
        ContextItem loaderIdentity) {

    ZeroCfaServiceLoaderIdentity {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(loaderIdentity, "loaderIdentity");
    }
}
