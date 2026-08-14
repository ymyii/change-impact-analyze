package io.github.dependencyanalysis.callgraph.model;

import java.util.Objects;

/**
 * Immutable identifier for a method
 * in the call graph. Uses JVM internal
 * name for owner and JVM descriptor.
 *
 * @param owner      owner class
 *                   internal name
 * @param name       method name
 * @param descriptor method JVM
 *                   descriptor
 * @param module     module name
 * @param sourceId logical source identity
 */
public record MethodId(
        String owner,
        String name,
        String descriptor,
        String module,
        String sourceId) {

    /**
     * Compact constructor with
     * null validation.
     */
    public MethodId {
        Objects.requireNonNull(
                owner, "owner");
        Objects.requireNonNull(
                name, "name");
        Objects.requireNonNull(
                descriptor, "descriptor");
        Objects.requireNonNull(
                module, "module");
        Objects.requireNonNull(
                sourceId, "sourceId");
    }
}
