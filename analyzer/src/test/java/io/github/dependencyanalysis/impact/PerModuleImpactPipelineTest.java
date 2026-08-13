package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.ModelKind;
import io.github.dependencyanalysis.callgraph.ModelLimitation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Module analysis coverage outcome selection. */
class PerModuleImpactPipelineTest {

    @Test
    void scopeValidationWarningIsInconclusive() {
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION);
    }

    @Test
    void existingCoverageReasonsKeepPriority() {
        final List<CoverageLimitation> all = List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_INVOKEDYNAMIC_MODEL));
        assertThat(ModuleCoverageReducer.reduce(true, all))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_BYTECODE_DIFF);
        assertThat(ModuleCoverageReducer.reduce(false, all))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_INVOKEDYNAMIC_MODEL);
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL);
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER);
        assertThat(ModuleCoverageReducer.reduce(false, List.of()))
                .isEqualTo(ModuleAnalysisReason.NONE);
    }

    @Test
    void dependencyBodyBoundaryMakesModuleInconclusive() {
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY);
    }

    @Test
    void invalidClassForNameLiteralMakesModuleInconclusive() {
        final ModelLimitation limitation = new ModelLimitation(
                ModelKind.REFLECTION,
                "CLASS_FOR_NAME_LITERAL_INVALID",
                ModuleAnalysisReason.INCONCLUSIVE_REFLECTION,
                "LExample.invalid()V|pc=1",
                "literal=<empty>");

        assertThat(ModuleCoverageReducer.reduce(false,
                List.of(limitation))).isEqualTo(
                ModuleAnalysisReason.INCONCLUSIVE_REFLECTION);
    }

    private CoverageLimitation limitation(
            final ModuleAnalysisReason reason) {
        return new CoverageLimitation() {
            @Override
            public ModuleAnalysisReason reason() {
                return reason;
            }

            @Override
            public String stableKey() {
                return reason.name();
            }

            @Override
            public String summary() {
                return reason.name();
            }
        };
    }
}
