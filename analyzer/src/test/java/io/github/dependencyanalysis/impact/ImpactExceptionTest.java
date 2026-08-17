package io.github.dependencyanalysis.impact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests actionable QueryNode worker failure wrapping. */
class ImpactExceptionTest {

    @Test
    void queryFailurePreservesOutOfMemoryRootCause() {
        final OutOfMemoryError cause =
                new OutOfMemoryError("Java heap space");

        final ImpactException failure =
                ModuleImpactTracer.queryFailure(cause);

        assertThat(failure)
                .hasMessage("QueryNode query failed; cause="
                        + "java.lang.OutOfMemoryError: Java heap space");
        assertThat(failure.getCause()).isSameAs(cause);
    }

    @Test
    void queryFailureHandlesMissingCauseMessage() {
        final AssertionError cause = new AssertionError();

        final ImpactException failure = ModuleImpactTracer.queryFailure(cause);

        assertThat(failure)
                .hasMessage("QueryNode query failed; cause="
                        + "java.lang.AssertionError");
        assertThat(failure.getCause()).isSameAs(cause);
    }
}
