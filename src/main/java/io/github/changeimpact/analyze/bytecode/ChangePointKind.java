package io.github.changeimpact.analyze.bytecode;

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
    FIELD_DESCRIPTOR_CHANGED
}
