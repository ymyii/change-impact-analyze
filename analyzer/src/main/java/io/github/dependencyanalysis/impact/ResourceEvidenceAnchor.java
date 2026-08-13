package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * ServiceLoader resource evidence anchor.
 *
 * @param resourcePath service configuration resource path
 * @param serviceInternalName service internal name
 * @param providerInternalName provider internal name
 */
public record ResourceEvidenceAnchor(
        String resourcePath,
        String serviceInternalName,
        String providerInternalName) implements EvidenceAnchor {

    /** Validates normalized resource identities. */
    public ResourceEvidenceAnchor {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(serviceInternalName, "serviceInternalName");
        Objects.requireNonNull(providerInternalName, "providerInternalName");
    }

    @Override
    public String stableKey() {
        return resourcePath + "|" + serviceInternalName + "|"
                + providerInternalName;
    }
}
