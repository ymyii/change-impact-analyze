package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import java.util.Objects;

/**
 * Immutable command-wide configuration retained in an analysis result.
 *
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyAnalysisScope requested dependency method-body scope
 * @param jdkModel command-wide JDK Method Model selection
 */
public record AnalysisRunConfiguration(
        EntrypointSelection entrypointSelection,
        CallGraphAlgorithm callGraphAlgorithm,
        WalaReflectionOptions reflectionOptions,
        DependencyAnalysisScopeMode dependencyAnalysisScope,
        JdkModelSelection jdkModel) {

    /** Validates command-wide configuration. */
    public AnalysisRunConfiguration {
        Objects.requireNonNull(entrypointSelection, "entrypointSelection");
        Objects.requireNonNull(callGraphAlgorithm, "callGraphAlgorithm");
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
        Objects.requireNonNull(dependencyAnalysisScope,
                "dependencyAnalysisScope");
        Objects.requireNonNull(jdkModel, "jdkModel");
    }

    /** Compatibility constructor using the default JDK model. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope) {
        this(selection, algorithm, reflection, dependencyScope,
                JdkModelSelection.defaultSelection());
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
                WalaReflectionOptions.defaultOptions(),
                DependencyAnalysisScopeMode.defaultMode(),
                JdkModelSelection.defaultSelection());
    }

    /** Compatibility constructor using the default dependency scope. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection) {
        this(selection, algorithm, reflection,
                DependencyAnalysisScopeMode.defaultMode(),
                JdkModelSelection.defaultSelection());
    }
}
