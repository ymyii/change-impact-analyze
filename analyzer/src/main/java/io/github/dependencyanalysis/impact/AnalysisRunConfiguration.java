package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import java.util.Objects;

/**
 * Immutable command-wide configuration retained in an analysis result.
 *
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param reflectionOptions command-wide WALA ReflectionOptions
 */
public record AnalysisRunConfiguration(
        EntrypointSelection entrypointSelection,
        CallGraphAlgorithm callGraphAlgorithm,
        WalaReflectionOptions reflectionOptions) {

    /** Validates command-wide configuration. */
    public AnalysisRunConfiguration {
        Objects.requireNonNull(entrypointSelection, "entrypointSelection");
        Objects.requireNonNull(callGraphAlgorithm, "callGraphAlgorithm");
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
    }

    /**
     * Creates configuration with default WALA ReflectionOptions.
     *
     * @param selection PROJECT entrypoint boundary
     * @param algorithm Call Graph algorithm
     */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm) {
        this(selection, algorithm,
                WalaReflectionOptions.defaultOptions());
    }
}
