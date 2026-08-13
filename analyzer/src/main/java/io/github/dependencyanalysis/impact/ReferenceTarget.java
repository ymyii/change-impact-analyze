package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Stable binary target identity of one reference.
 *
 * @param owner internal owner or service name
 * @param name member/provider name, empty for class references
 * @param descriptor JVM descriptor or resource path, empty when absent
 */
public record ReferenceTarget(
        String owner,
        String name,
        String descriptor) {

    /** Validates target text. */
    public ReferenceTarget {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    /** @return deterministic target identity */
    public String stableKey() {
        return owner + "#" + name + descriptor;
    }
}
