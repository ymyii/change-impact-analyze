package io.github.dependencyanalysis.callgraph.strategy;

import java.util.Objects;

/**
 * Immutable command-wide Call Graph algorithm configuration.
 *
 * @param algorithm selected algorithm
 * @param kObjDepth k-object receiver allocation-string depth
 * @param reflectionOptions WALA ReflectionOptions
 */
public record CallGraphConfiguration(
        CallGraphAlgorithm algorithm,
        int kObjDepth,
        WalaReflectionOptions reflectionOptions) {

    /** Validates one command-wide graph configuration. */
    public CallGraphConfiguration {
        Objects.requireNonNull(algorithm, "algorithm");
        kObjDepth = CallGraphAlgorithm.requireValidKObjDepth(kObjDepth);
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
    }

    /**
     * Creates a configuration with the default k-object depth.
     *
     * @param selectedAlgorithm Call Graph algorithm
     * @param selectedReflectionOptions WALA ReflectionOptions
     * @return validated configuration
     */
    public static CallGraphConfiguration withDefaultKObjDepth(
            final CallGraphAlgorithm selectedAlgorithm,
            final WalaReflectionOptions selectedReflectionOptions) {
        return new CallGraphConfiguration(selectedAlgorithm,
                CallGraphAlgorithm.defaultKObjDepth(),
                selectedReflectionOptions);
    }
}
