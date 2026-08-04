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

    /**
     * Creates a new field info.
     *
     * @param nam  field name
     * @param desc field descriptor
     */
    FieldInfo(
            final String nam,
            final String desc) {
        this.name =
                Objects.requireNonNull(
                        nam, "name");
        this.descriptor =
                Objects.requireNonNull(
                        desc, "descriptor");
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
                        that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, descriptor);
    }

    @Override
    public String toString() {
        return "FieldInfo{"
                + "name=" + name
                + ", descriptor="
                + descriptor
                + '}';
    }
}
