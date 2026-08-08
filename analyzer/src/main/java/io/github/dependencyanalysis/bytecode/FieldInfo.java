package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Immutable field information
 * extracted from a class file.
 * Package-private.
 */
final class FieldInfo {

    /** Field name. */
    private final String name;

    /** Field descriptor. */
    private final String descriptor;

    /** Normalized JVM member access. */
    private final JvmAccess access;

    /**
     * Creates a new field info.
     *
     * @param nam  field name
     * @param desc field descriptor
     * @param fieldAccess normalized member access
     */
    FieldInfo(
            final String nam,
            final String desc,
            final JvmAccess fieldAccess) {
        this.name =
                Objects.requireNonNull(
                        nam, "name");
        this.descriptor =
                Objects.requireNonNull(
                        desc, "descriptor");
        this.access = Objects.requireNonNull(fieldAccess, "access");
    }

    /**
     * Returns the field name.
     *
     * @return field name
     */
    String getName() {
        return name;
    }

    /**
     * Returns the field descriptor.
     *
     * @return field descriptor
     */
    String getDescriptor() {
        return descriptor;
    }

    /** @return normalized JVM member access */
    JvmAccess getAccess() {
        return access;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldInfo)) {
            return false;
        }
        final FieldInfo that =
                (FieldInfo) o;
        return name.equals(that.name)
                && descriptor.equals(
                        that.descriptor)
                && access == that.access;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, descriptor, access);
    }

    @Override
    public String toString() {
        return "FieldInfo{"
                + "name=" + name
                + ", descriptor="
                + descriptor
                + ", access=" + access
                + '}';
    }
}
