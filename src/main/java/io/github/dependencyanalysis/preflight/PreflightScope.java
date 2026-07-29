package io.github.dependencyanalysis.preflight;

/** Scope of a preflight check. */
public enum PreflightScope {
    /** Whole command. */
    COMMAND,

    /** Repository snapshot. */
    SNAPSHOT,

    /** One Maven reactor. */
    REACTOR
}
