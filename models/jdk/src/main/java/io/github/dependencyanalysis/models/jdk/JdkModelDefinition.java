package io.github.dependencyanalysis.models.jdk;

import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one version-specific JDK model catalog resource.
 *
 * @param modelId stable model identifier
 * @param resourceAnchor class whose loader owns the catalog
 * @param catalogResource absolute classpath resource path
 */
public record JdkModelDefinition(
        String modelId,
        Class<?> resourceAnchor,
        String catalogResource) {

    /** Stable model identifier format. */
    private static final Pattern MODEL_ID = Pattern.compile(
            "[a-z][a-z0-9-]*");

    /** Validates one immutable model definition. */
    public JdkModelDefinition {
        modelId = Objects.requireNonNull(modelId, "modelId");
        resourceAnchor = Objects.requireNonNull(
                resourceAnchor, "resourceAnchor");
        catalogResource = Objects.requireNonNull(
                catalogResource, "catalogResource");
        if (!MODEL_ID.matcher(modelId).matches()) {
            throw new IllegalArgumentException(
                    "modelId must match " + MODEL_ID.pattern());
        }
        if (!catalogResource.startsWith("/")
                || catalogResource.length() == 1) {
            throw new IllegalArgumentException(
                    "catalogResource must be an absolute classpath resource");
        }
    }

    InputStream openCatalog() {
        final InputStream input = resourceAnchor.getResourceAsStream(
                catalogResource);
        if (input == null) {
            throw new JdkModelException(
                    "JDK model catalog is unavailable for " + modelId
                            + ": " + catalogResource);
        }
        return input;
    }
}
