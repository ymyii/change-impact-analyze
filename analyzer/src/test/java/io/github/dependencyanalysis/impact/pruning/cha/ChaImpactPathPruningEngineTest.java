package io.github.dependencyanalysis.impact.pruning.cha;

import io.github.dependencyanalysis.impact.ImpactException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests fixed extension execution failure boundaries. */
class ChaImpactPathPruningEngineTest {

    @Test
    void extensionFailureIsRetainedFailOpen() {
        final ChaImpactPathPruningEngine.ExtensionRuntime runtime =
                new ChaImpactPathPruningEngine.ExtensionRuntime(
                        new ThrowingExtension(
                                new IllegalStateException("broken")), false);

        assertThat(runtime.evaluate(
                new PruningEdge(null, null)).decision())
                .isEqualTo(PruningDecision.UNKNOWN);
        assertThat(runtime.summary().metrics().failOpenErrors()).isEqualTo(1);
        assertThat(runtime.summary().metrics().unknown()).isEqualTo(1);
    }

    @Test
    void interruptionFailurePropagates() {
        final ImpactException interruption =
                new ImpactException("Impact Path pruning interrupted");
        final ChaImpactPathPruningEngine.ExtensionRuntime runtime =
                new ChaImpactPathPruningEngine.ExtensionRuntime(
                        new ThrowingExtension(interruption), false);

        assertThatThrownBy(() -> runtime.evaluate(
                new PruningEdge(null, null)))
                .isSameAs(interruption);
    }

    /** Test extension throwing a configured failure. */
    private static final class ThrowingExtension
            implements ImpactPathPruningExtension {

        /** Configured failure. */
        private final RuntimeException failure;

        /** Evidence counters. */
        private final ExtensionCounters counters = new ExtensionCounters();

        ThrowingExtension(final RuntimeException configuredFailure) {
            failure = configuredFailure;
        }

        @Override
        public String identifier() {
            return "throwing-test-extension";
        }

        @Override
        public Object cacheKey(final PruningEdge edge) {
            return edge;
        }

        @Override
        public PruningEvaluation evaluate(final PruningEdge edge) {
            throw failure;
        }

        @Override
        public ExtensionCounters counters() {
            return counters;
        }
    }
}
