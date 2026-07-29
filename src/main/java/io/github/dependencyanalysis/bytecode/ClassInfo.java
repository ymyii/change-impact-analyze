package io.github.dependencyanalysis.bytecode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable class information
 * extracted from a class file.
 * Package-private.
 */
final class ClassInfo {

    /** Internal class name. */
    private final String internalName;

    /** Methods in this class. */
    private final List<MethodInfo> methods;

    /** Fields in this class. */
    private final List<FieldInfo> fields;

    /**
     * Creates a new class info.
     *
     * @param name   internal name
     * @param mtds   method list
     * @param flds   field list
     */
    ClassInfo(
            final String name,
            final List<MethodInfo> mtds,
            final List<FieldInfo> flds) {
        this.internalName =
                Objects.requireNonNull(
                        name, "internalName");
        this.methods =
                Collections.unmodifiableList(
                        new ArrayList<>(mtds));
        this.fields =
                Collections.unmodifiableList(
                        new ArrayList<>(flds));
    }

    /**
     * Returns the internal name.
     *
     * @return internal class name
     */
    String getInternalName() {
        return internalName;
    }

    /**
     * Returns the method list.
     *
     * @return unmodifiable method list
     */
    List<MethodInfo> getMethods() {
        return methods;
    }

    /**
     * Returns the field list.
     *
     * @return unmodifiable field list
     */
    List<FieldInfo> getFields() {
        return fields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassInfo)) {
            return false;
        }
        final ClassInfo that =
                (ClassInfo) o;
        return internalName.equals(
                        that.internalName)
                && methods.equals(
                        that.methods)
                && fields.equals(
                        that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                internalName, methods,
                fields);
    }

    @Override
    public String toString() {
        return "ClassInfo{"
                + "internalName="
                + internalName
                + ", methods=" + methods
                + ", fields=" + fields
                + '}';
    }
}
