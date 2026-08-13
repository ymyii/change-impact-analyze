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

    /** A class became package-private. */
    CLASS_ACCESS_NARROWED,

    /** A method was added. */
    METHOD_ADDED,

    /** A method was removed. */
    METHOD_REMOVED,

    /** A method descriptor changed. */
    METHOD_DESCRIPTOR_CHANGED,

    /** A method body changed. */
    METHOD_BODY_CHANGED,

    /** A method or constructor access narrowed. */
    METHOD_ACCESS_NARROWED,

    /** A field was added. */
    FIELD_ADDED,

    /** A field was removed. */
    FIELD_REMOVED,

    /** A field descriptor changed. */
    FIELD_DESCRIPTOR_CHANGED,

    /** A field access narrowed. */
    FIELD_ACCESS_NARROWED,

    /** A valid ServiceLoader provider registration was removed. */
    SERVICE_PROVIDER_REGISTRATION_REMOVED;

    /**
     * Default set of included change
     * point kinds. Contains the ten
     * non-ADDED kinds that represent
     * removals, modifications and a
     * removed service registration.
     */
    public static final Set<ChangePointKind>
            DEFAULT_INCLUDED_KINDS;

    static {
        DEFAULT_INCLUDED_KINDS =
                Collections.unmodifiableSet(
                        EnumSet.of(
                                CLASS_REMOVED,
                                CLASS_ACCESS_NARROWED,
                                METHOD_REMOVED,
                                METHOD_DESCRIPTOR_CHANGED,
                                METHOD_BODY_CHANGED,
                                METHOD_ACCESS_NARROWED,
                                FIELD_REMOVED,
                                FIELD_DESCRIPTOR_CHANGED,
                                FIELD_ACCESS_NARROWED,
                                SERVICE_PROVIDER_REGISTRATION_REMOVED));
    }

    /** @return true for strict JVM access narrowing kinds */
    public boolean isAccessNarrowing() {
        return this == CLASS_ACCESS_NARROWED
                || this == METHOD_ACCESS_NARROWED
                || this == FIELD_ACCESS_NARROWED;
    }
}
