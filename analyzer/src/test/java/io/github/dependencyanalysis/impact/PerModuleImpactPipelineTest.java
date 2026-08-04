package io.github.dependencyanalysis.impact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Module analysis coverage outcome selection. */
class PerModuleImpactPipelineTest {

    @Test
    void scopeValidationWarningIsInconclusive() {
        assertThat(PerModuleImpactPipeline.coverageReason(
                false, false, true))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION);
    }

    @Test
    void existingCoverageReasonsKeepPriority() {
        assertThat(PerModuleImpactPipeline.coverageReason(
                true, true, true))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_BYTECODE_DIFF);
        assertThat(PerModuleImpactPipeline.coverageReason(
                false, true, true))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER);
        assertThat(PerModuleImpactPipeline.coverageReason(
                false, false, false))
                .isEqualTo(ModuleAnalysisReason.NONE);
    }
}
