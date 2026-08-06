package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests stable algorithm identifiers and WALA instance policies. */
class CallGraphAlgorithmTest {

    @Test
    void zeroCfaUsesClassBasedAllocationAndConstantKeys() {
        assertThat(CallGraphAlgorithm.ZERO_CFA.instancePolicy())
                .isEqualTo(ZeroXInstanceKeys.CONSTANT_SPECIFIC)
                .matches(policy -> (policy
                        & ZeroXInstanceKeys.ALLOCATIONS) == 0)
                .matches(policy -> (policy
                        & ZeroXInstanceKeys.SMUSH_MANY) == 0);
    }

    @Test
    void optimizedZeroOneCfaRetainsCompletePolicy() {
        final int expected = ZeroXInstanceKeys.ALLOCATIONS
                | ZeroXInstanceKeys.CONSTANT_SPECIFIC
                | ZeroXInstanceKeys.SMUSH_MANY
                | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS
                | ZeroXInstanceKeys.SMUSH_STRINGS
                | ZeroXInstanceKeys.SMUSH_THROWABLES;

        assertThat(CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA
                .instancePolicy()).isEqualTo(expected);
    }

    @Test
    void parsesOnlyStableIdentifiersCaseInsensitively() {
        assertThat(CallGraphAlgorithm.defaultAlgorithm())
                .isEqualTo(CallGraphAlgorithm.ZERO_CFA);
        assertThat(CallGraphAlgorithm.parse("ZERO-CFA"))
                .isEqualTo(CallGraphAlgorithm.ZERO_CFA);
        assertThat(CallGraphAlgorithm.parse("Optimized-0-1-CFA"))
                .isEqualTo(CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
        assertThatThrownBy(() -> CallGraphAlgorithm.parse("zerocfa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero-cfa, optimized-0-1-cfa");
    }
}
