package io.github.dependencyanalysis.bytecode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

// Wiki: wiki/features/bytecode-diff-engine.md - Bytecode change point kind
/**
 * Enumeration of bytecode change
 * point kinds detected by the
 * bytecode diff engine.
 */
public enum ChangePointKind {

    /** A class was added. */
    CLASS_ADDED,

    /** A class was removed. */
    CLASS_REMOVED,

    /** A method was added. */
    METHOD_ADDED,

    /** A method was removed. */
    METHOD_REMOVED,

    /** A method descriptor changed. */
    METHOD_DESCRIPTOR_CHANGED,

    /** A method body changed. */
    METHOD_BODY_CHANGED,

    /** A field was added. */
    FIELD_ADDED,

    /** A field was removed. */
    FIELD_REMOVED,

    /** A field descriptor changed. */
    FIELD_DESCRIPTOR_CHANGED;

    /**
     * Default set of included change
     * point kinds. Contains the 6
     * non-ADDED kinds that represent
     * removals and modifications.
     */
    public static final Set<ChangePointKind>
            DEFAULT_INCLUDED_KINDS;

    static {
        DEFAULT_INCLUDED_KINDS =
                Collections.unmodifiableSet(
                        EnumSet.of(
                                CLASS_REMOVED,
                                METHOD_REMOVED,
                                METHOD_DESCRIPTOR_CHANGED,
                                METHOD_BODY_CHANGED,
                                FIELD_REMOVED,
                                FIELD_DESCRIPTOR_CHANGED));
    }
}
