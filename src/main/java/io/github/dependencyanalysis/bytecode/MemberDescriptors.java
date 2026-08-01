package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Immutable old/new member descriptor pair.
 */
public final class MemberDescriptors {

    /** Old descriptor. */
    private final String oldDescriptor;

    /** New descriptor. */
    private final String newDescriptor;

    /**
     * Creates a descriptor pair.
     *
     * @param oldDesc old descriptor or null
     * @param newDesc new descriptor or null
     */
    public MemberDescriptors(
            final String oldDesc,
            final String newDesc) {
        oldDescriptor = oldDesc;
        newDescriptor = newDesc;
    }

    /** @return old descriptor or null */
    public String getOldDescriptor() {
        return oldDescriptor;
    }

    /** @return new descriptor or null */
    public String getNewDescriptor() {
        return newDescriptor;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MemberDescriptors)) {
            return false;
        }
        final MemberDescriptors that =
                (MemberDescriptors) object;
        return Objects.equals(
                oldDescriptor,
                that.oldDescriptor)
                && Objects.equals(
                newDescriptor,
                that.newDescriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                oldDescriptor,
                newDescriptor);
    }

    @Override
    public String toString() {
        return "MemberDescriptors{"
                + "old=" + oldDescriptor
                + ", new=" + newDescriptor
                + '}';
    }
}
