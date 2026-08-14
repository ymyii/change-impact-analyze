package io.github.dependencyanalysis.impact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultRefinementSelectionTest {

    @Test
    void parsesNoneAndStableAlgorithmOrder() {
        assertThat(ResultRefinementSelection.parse("NONE").isEmpty())
                .isTrue();
        final ResultRefinementSelection selection =
                ResultRefinementSelection.parse(
                        " ssa-equivalence,"
                                + "CHA-LOCAL-RECEIVER-INFERENCE,"
                                + "ssa-equivalence ");

        assertThat(selection.algorithms()).containsExactly(
                ResultRefinementAlgorithm.CHA_LOCAL_RECEIVER_INFERENCE,
                ResultRefinementAlgorithm.SSA_EQUIVALENCE);
        assertThat(selection.identifiers()).containsExactly(
                "cha-local-receiver-inference", "ssa-equivalence");
        assertThat(selection.toString()).isEqualTo(
                "cha-local-receiver-inference,ssa-equivalence");
    }

    @Test
    void rejectsEmptyUnknownAndMixedNone() {
        assertThatThrownBy(() -> ResultRefinementSelection.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResultRefinementSelection.parse(
                "ssa-equivalence,"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResultRefinementSelection.parse("future"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResultRefinementSelection.parse(
                "none,ssa-equivalence"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none cannot be combined");
    }
}
