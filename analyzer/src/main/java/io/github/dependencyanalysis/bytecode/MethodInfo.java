package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Immutable method information
 * extracted from a class file
 * including its body hash.
 * Package-private.
 */
final class MethodInfo {

    /** Method name. */
    private final String name;

    /** Method descriptor. */
    private final String descriptor;

    /** Body hash (null if abstract/native). */
    private final String bodyHash;

    /**
     * Creates a new method info.
     *
     * @param nam  method name
     * @param desc method descriptor
     * @param hash body hash or null
     */
    MethodInfo(
            final String nam,
            final String desc,
            final String hash) {
        this.name =
                Objects.requireNonNull(
                        nam, "name");
        this.descriptor =
                Objects.requireNonNull(
                        desc, "descriptor");
        this.bodyHash = hash;
    }

    /**
     * Returns the method name.
     *
     * @return method name
     */
    String getName() {
        return name;
    }

    /**
     * Returns the method descriptor.
     *
     * @return method descriptor
     */
    String getDescriptor() {
        return descriptor;
    }

    /**
     * Returns the body hash.
     * Null for abstract or native.
     *
     * @return body hash or null
     */
    String getBodyHash() {
        return bodyHash;
    }

    /**
     * Returns a unique key for
     * matching methods by name and
     * descriptor.
     *
     * @return name:descriptor key
     */
    String key() {
        return name + ":" + descriptor;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodInfo)) {
            return false;
        }
        final MethodInfo that =
                (MethodInfo) o;
        return name.equals(that.name)
                && descriptor.equals(
                        that.descriptor)
                && Objects.equals(
                        bodyHash,
                        that.bodyHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, descriptor,
                bodyHash);
    }

    @Override
    public String toString() {
        return "MethodInfo{"
                + "name=" + name
                + ", descriptor="
                + descriptor
                + ", bodyHash="
                + bodyHash
                + '}';
    }
}
