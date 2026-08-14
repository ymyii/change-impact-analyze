package io.github.dependencyanalysis.callgraph.strategy;

import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import java.util.Objects;

/** Algorithm-dependent defaults and validation shared by CLI and Java API. */
public final class CallGraphPolicy {

    private CallGraphPolicy() {
    }

    /**
     * @param algorithm selected Call Graph algorithm
     * @return default JDK model for one algorithm
     */
    public static JdkModelSelection defaultJdkModel(
            final CallGraphAlgorithm algorithm) {
        return Objects.requireNonNull(algorithm, "algorithm")
                == CallGraphAlgorithm.CHA
                ? JdkModelSelection.NONE : JdkModelSelection.JDK8;
    }

    /**
     * Validates an effective algorithm/model pair.
     *
     * @param algorithm selected algorithm
     * @param jdkModel effective JDK model
     */
    public static void validate(
            final CallGraphAlgorithm algorithm,
            final JdkModelSelection jdkModel) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(jdkModel, "jdkModel");
        if (algorithm == CallGraphAlgorithm.CHA
                && jdkModel != JdkModelSelection.NONE) {
            throw new IllegalArgumentException(
                    "--jdk-model jdk8 is not supported with "
                            + "--call-graph-algorithm cha; use none");
        }
    }
}
