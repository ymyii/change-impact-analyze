package io.github.dependencyanalysis.callgraph;

/** Real invocation or analyzer-owned protocol edge in an Impact Path. */
public enum CallEdgeKind {
    /** Virtual invocation. */
    INVOKE_VIRTUAL,
    /** Static invocation. */
    INVOKE_STATIC,
    /** Special invocation. */
    INVOKE_SPECIAL,
    /** Interface invocation. */
    INVOKE_INTERFACE,
    /** ServiceLoader provider-constructor protocol edge. */
    SERVICE_LOADER
}
