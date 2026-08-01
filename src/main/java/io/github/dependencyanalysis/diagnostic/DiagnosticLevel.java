package io.github.dependencyanalysis.diagnostic;

/**
 * Severity level for a diagnostic event.
 */
public enum DiagnosticLevel {

    /** Fine-grained analysis evidence. */
    TRACE,

    /** Analysis decisions and configuration. */
    DEBUG,

    /** Informational. */
    INFO,

    /** Warning. */
    WARN,

    /** Error. */
    ERROR
}
