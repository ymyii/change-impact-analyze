package io.github.dependencyanalysis.reactor;

/** Maven reactor scope selected from the requested POM. */
public enum ReactorScopeMode {

    /** Requested POM is an aggregator; analyze its active reactor. */
    FULL_REACTOR,

    /** Requested POM is owned by an ancestor aggregator. */
    SINGLE_MODULE,

    /** No ancestor aggregator owns the requested POM. */
    STANDALONE
}
