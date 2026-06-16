package io.github.changeimpact.analyze.impact;

/**
 * Enumeration of reasons why
 * a change point was not
 * reported as an impact path.
 */
public enum NotReportedReason {

    /** Change kind is not applicable
     *  for impact tracing (e.g.
     *  CLASS_ADDED, METHOD_ADDED,
     *  FIELD_ADDED). */
    CHANGE_KIND_NOT_APPLICABLE,

    /** No application method
     *  references the change
     *  point in bytecode. */
    NO_SEED_FOUND,

    /** Seed method was not found
     *  in the call graph, so the
     *  call chain is incomplete. */
    INCOMPLETE_CHAIN
}
