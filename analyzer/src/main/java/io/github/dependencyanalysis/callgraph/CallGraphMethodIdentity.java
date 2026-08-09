package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * Context-independent Method identity used by benchmark diagnostics.
 *
 * @param owner declaring class binary name
 * @param name Method name
 * @param descriptor JVM Method descriptor
 * @param origin code origin
 */
public record CallGraphMethodIdentity(
        String owner,
        String name,
        String descriptor,
        CodeOrigin origin) implements Comparable<CallGraphMethodIdentity> {

    /** Validates required identity parts. */
    public CallGraphMethodIdentity {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(origin, "origin");
    }

    /** @return deterministic identity including origin */
    public String stableKey() {
        return owner + "#" + name + descriptor + "@" + origin;
    }

    @Override
    public int compareTo(final CallGraphMethodIdentity other) {
        return stableKey().compareTo(other.stableKey());
    }
}
