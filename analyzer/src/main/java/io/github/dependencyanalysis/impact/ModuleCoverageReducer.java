package io.github.dependencyanalysis.impact;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Reduces typed limitations to the single stable module status reason. */
final class ModuleCoverageReducer {

    /** Stable highest-first reason precedence. */
    private static final Map<ModuleAnalysisReason, Integer> PRECEDENCE =
            Map.of(
                    ModuleAnalysisReason
                            .INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY, 1,
                    ModuleAnalysisReason.INCONCLUSIVE_INVOKEDYNAMIC_MODEL, 2,
                    ModuleAnalysisReason.INCONCLUSIVE_METHOD_HANDLE_MODEL, 3,
                    ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER, 4,
                    ModuleAnalysisReason.INCONCLUSIVE_REFLECTION, 5,
                    ModuleAnalysisReason.INCONCLUSIVE_SCOPE_VALIDATION, 6);

    private ModuleCoverageReducer() {
    }

    static ModuleAnalysisReason reduce(
            final boolean diffFailed,
            final Collection<? extends CoverageLimitation> limitations) {
        Objects.requireNonNull(limitations, "limitations");
        if (diffFailed) {
            return ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF;
        }
        return limitations.stream()
                .map(CoverageLimitation::reason)
                .filter(PRECEDENCE::containsKey)
                .min(Comparator.comparingInt(PRECEDENCE::get))
                .orElse(ModuleAnalysisReason.NONE);
    }
}
