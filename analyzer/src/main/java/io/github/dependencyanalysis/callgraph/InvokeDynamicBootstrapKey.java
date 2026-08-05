package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * Exact invokedynamic bootstrap method identity.
 *
 * @param owner bootstrap internal owner
 * @param name bootstrap method name
 * @param descriptor bootstrap method descriptor
 */
public record InvokeDynamicBootstrapKey(
        String owner,
        String name,
        String descriptor) {

    /** Validates normalized key fields. */
    public InvokeDynamicBootstrapKey {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
