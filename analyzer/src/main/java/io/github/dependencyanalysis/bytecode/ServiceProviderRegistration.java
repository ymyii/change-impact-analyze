package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;

/**
 * Typed ServiceLoader service/provider resource subject.
 *
 * @param resourcePath service resource path
 * @param serviceInternalName service internal name
 * @param providerInternalName provider internal name
 * @param targetArtifact target artifact coordinate
 */
public record ServiceProviderRegistration(
        String resourcePath,
        String serviceInternalName,
        String providerInternalName,
        ArtifactCoord targetArtifact) {

    /** Validates stable normalized names. */
    public ServiceProviderRegistration {
        requireText(resourcePath, "resourcePath");
        requireText(serviceInternalName, "serviceInternalName");
        requireText(providerInternalName, "providerInternalName");
        Objects.requireNonNull(targetArtifact, "targetArtifact");
        if (!resourcePath.equals("META-INF/services/"
                + serviceInternalName.replace('/', '.'))) {
            throw new IllegalArgumentException(
                    "Resource path must identify the service");
        }
    }

    /** @return deterministic identity */
    public String stableKey() {
        return targetArtifact + "|" + resourcePath + "|"
                + serviceInternalName + "|" + providerInternalName;
    }

    private static void requireText(
            final String value, final String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
