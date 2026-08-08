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

    /** Normalized JVM member access. */
    private final JvmAccess access;

    /** Body hash (null if abstract/native). */
    private final String bodyHash;

    /**
     * Creates a new method info.
     *
     * @param nam  method name
     * @param desc method descriptor
     * @param methodAccess normalized member access
     * @param hash body hash or null
     */
    MethodInfo(
            final String nam,
            final String desc,
            final JvmAccess methodAccess,
            final String hash) {
        this.name =
                Objects.requireNonNull(
                        nam, "name");
        this.descriptor =
                Objects.requireNonNull(
                        desc, "descriptor");
        this.access = Objects.requireNonNull(methodAccess, "access");
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

    /** @return normalized JVM member access */
    JvmAccess getAccess() {
        return access;
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
                && access == that.access
                && Objects.equals(
                        bodyHash,
                        that.bodyHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, descriptor, access,
                bodyHash);
    }

    @Override
    public String toString() {
        return "MethodInfo{"
                + "name=" + name
                + ", descriptor="
                + descriptor
                + ", access=" + access
                + ", bodyHash="
                + bodyHash
                + '}';
    }
}
