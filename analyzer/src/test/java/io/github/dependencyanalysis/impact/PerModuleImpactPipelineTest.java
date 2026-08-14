package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.ModelKind;
import io.github.dependencyanalysis.callgraph.ModelLimitation;
import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Module analysis coverage outcome selection. */
class PerModuleImpactPipelineTest {

    /** Odd multi-core test input. */
    private static final int THREE_PROCESSORS = 3;

    /** Eight-core test input. */
    private static final int EIGHT_PROCESSORS = 8;

    /** Half of eight processors. */
    private static final int FOUR_WORKERS = 4;

    @Test
    void analysisParallelismDefaultsToHalfAvailableProcessors() {
        assertThat(ImpactCommand.defaultAnalysisParallelism(1)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(2)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(
                THREE_PROCESSORS)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(
                EIGHT_PROCESSORS)).isEqualTo(FOUR_WORKERS);
    }

    @Test
    void resultRefinementIsDisabledByCompatibilityConfiguration() {
        final AnalysisRunConfiguration configuration =
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA);

        assertThat(configuration.resultRefinements().isEmpty()).isTrue();
    }

    @Test
    void resultRefinementsCanBeEnabledExplicitly() {
        final AnalysisRunConfiguration configuration =
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA,
                        CallGraphAlgorithm.defaultKObjDepth(),
                        WalaReflectionOptions.defaultOptions(),
                        DependencyAnalysisScopeMode.defaultMode(),
                        JdkModelSelection.NONE,
                        ResultRefinementSelection.of(
                                ResultRefinementAlgorithm.SSA_EQUIVALENCE));

        assertThat(configuration.resultRefinements().algorithms())
                .containsExactly(ResultRefinementAlgorithm.SSA_EQUIVALENCE);
    }

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
