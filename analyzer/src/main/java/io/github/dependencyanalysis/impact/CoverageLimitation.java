package io.github.dependencyanalysis.impact;

/** Immutable typed reason for incomplete analysis coverage. */
public interface CoverageLimitation {

    /** @return module status reason represented by this limitation */
    ModuleAnalysisReason reason();

    /** @return deterministic identity independent of presentation text */
    String stableKey();

    /** @return one-way human-readable projection */
    String summary();
}
