package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Frozen business projection of one Call Graph limitation.
 *
 * @param reason business analysis reason
 * @param stableKey stable limitation identity
 * @param summary human-readable limitation summary
 */
record CallGraphCoverageLimitation(
        ModuleAnalysisReason reason,
        String stableKey,
        String summary) implements CoverageLimitation {

    CallGraphCoverageLimitation {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(stableKey, "stableKey");
        Objects.requireNonNull(summary, "summary");
        if (reason == ModuleAnalysisReason.NONE) {
            throw new IllegalArgumentException(
                    "Call Graph limitation must be inconclusive");
        }
    }
}
