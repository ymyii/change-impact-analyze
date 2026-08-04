package io.github.dependencyanalysis.impact;

/** Maven analysis scope selected from the requested POM. */
public enum AnalysisMode {

    /** Analyze every active module in the selected reactor. */
    REACTOR,

    /** Build the reactor closure but analyze only the requested leaf. */
    SINGLE_MODULE
}
