package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests stable algorithm identifiers and strategy selection. */
class CallGraphAlgorithmTest {

    @Test
    void factorySelectsOneIndependentStrategyPerAlgorithm() {
        final CallGraphStrategyFactory factory =
                new CallGraphStrategyFactory();
        final CallGraphAlgorithmStrategy rta = factory.create(
                CallGraphAlgorithm.RTA);
        final CallGraphAlgorithmStrategy zeroCfa = factory.create(
                CallGraphAlgorithm.ZERO_CFA);
        final CallGraphAlgorithmStrategy optimized = factory.create(
                CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);

        assertThat(rta)
                .isInstanceOf(RtaCallGraphStrategy.class);
        assertThat(rta.algorithm()).isEqualTo(CallGraphAlgorithm.RTA);
        assertThat(zeroCfa)
                .isInstanceOf(ZeroCfaCallGraphStrategy.class);
        assertThat(zeroCfa.algorithm())
                .isEqualTo(CallGraphAlgorithm.ZERO_CFA);
        assertThat(optimized)
                .isInstanceOf(OptimizedZeroOneCfaCallGraphStrategy.class);
        assertThat(optimized.algorithm())
                .isEqualTo(CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
    }

    @Test
    void parsesOnlyStableIdentifiersCaseInsensitively() {
        assertThat(CallGraphAlgorithm.defaultAlgorithm())
                .isEqualTo(CallGraphAlgorithm.RTA);
        assertThat(CallGraphAlgorithm.parse("RTA"))
                .isEqualTo(CallGraphAlgorithm.RTA);
        assertThat(CallGraphAlgorithm.parse("ZERO-CFA"))
                .isEqualTo(CallGraphAlgorithm.ZERO_CFA);
        assertThat(CallGraphAlgorithm.parse("Optimized-0-1-CFA"))
                .isEqualTo(CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
        assertThatThrownBy(() -> CallGraphAlgorithm.parse("zerocfa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "rta, zero-cfa, optimized-0-1-cfa");
    }

    @Test
    void walaReflectionOptionsDefaultAndParsingCoverNativeEnum() {
        assertThat(WalaReflectionOptions.defaultOptions().identifier())
                .isEqualTo("ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD");
        assertThat(WalaReflectionOptions.parse("full").identifier())
                .isEqualTo("FULL");
        assertThat(WalaReflectionOptions.supportedValues())
                .contains("NO_FLOW_TO_CASTS", "STRING_ONLY", "NONE");
        assertThatThrownBy(() -> WalaReflectionOptions.parse("one-flow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD");
    }

    @Test
    void strategyMetadataDeduplicatesAndSortsLimitations() {
        final ModelLimitation dynamic = limitation(
                ModelKind.INVOKEDYNAMIC, "dynamic", "b");
        final ModelLimitation handle = limitation(
                ModelKind.METHOD_HANDLE, "handle", "a");
        final ModelLimitation service = limitation(
                ModelKind.SERVICE_LOADER, "service", "c");
        final StrategyModelMetadata metadata = new StrategyModelMetadata(
                new DynamicCallEvidenceIndex(List.of()),
                List.of(service, dynamic, handle, dynamic),
                ServiceLoaderModelMetadata.empty(List.of(service, service)));

        assertThat(metadata.limitations()).containsExactly(
                dynamic, handle, service);
        assertThat(metadata.serviceLoader().limitations()).containsExactly(
                service);
    }

    @Test
    void modelLimitationRejectsReasonFromAnotherModel() {
        assertThatThrownBy(() -> new ModelLimitation(
                ModelKind.METHOD_HANDLE, "code",
                ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER,
                "location", "detail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match model");
    }

    private ModelLimitation limitation(
            final ModelKind model, final String code,
            final String location) {
        final ModuleAnalysisReason reason = switch (model) {
            case INVOKEDYNAMIC -> ModuleAnalysisReason
                    .INCONCLUSIVE_INVOKEDYNAMIC_MODEL;
            case METHOD_HANDLE -> ModuleAnalysisReason
                    .INCONCLUSIVE_METHOD_HANDLE_MODEL;
            case SERVICE_LOADER -> ModuleAnalysisReason
                    .INCONCLUSIVE_SERVICE_LOADER;
        };
        return new ModelLimitation(model, code, reason, location, "detail");
    }
}
