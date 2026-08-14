package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.CallGraphPolicy;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import java.util.Objects;

/**
 * Immutable command-wide configuration retained in an analysis result.
 *
 * @param entrypointSelection user-selected PROJECT entrypoint boundary
 * @param callGraphAlgorithm command-wide Call Graph algorithm
 * @param kObjDepth command-wide k-object receiver allocation-string depth
 * @param reflectionOptions command-wide WALA ReflectionOptions
 * @param dependencyAnalysisScope requested dependency method-body scope
 * @param jdkModel command-wide JDK Method Model selection
 * @param resultRefinements command-wide result-refinement selection
 */
public record AnalysisRunConfiguration(
        EntrypointSelection entrypointSelection,
        CallGraphAlgorithm callGraphAlgorithm,
        int kObjDepth,
        WalaReflectionOptions reflectionOptions,
        DependencyAnalysisScopeMode dependencyAnalysisScope,
        JdkModelSelection jdkModel,
        ResultRefinementSelection resultRefinements) {

    /** Validates command-wide configuration. */
    public AnalysisRunConfiguration {
        Objects.requireNonNull(entrypointSelection, "entrypointSelection");
        Objects.requireNonNull(callGraphAlgorithm, "callGraphAlgorithm");
        kObjDepth = CallGraphAlgorithm.requireValidKObjDepth(kObjDepth);
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
        Objects.requireNonNull(dependencyAnalysisScope,
                "dependencyAnalysisScope");
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(resultRefinements, "resultRefinements");
        CallGraphPolicy.validate(callGraphAlgorithm, jdkModel);
    }

    /** Compatibility constructor with result refinement disabled. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final int depth,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection selectedJdkModel) {
        this(selection, algorithm, depth, reflection, dependencyScope,
                selectedJdkModel,
                ResultRefinementSelection.defaultSelection());
    }

    /** Compatibility constructor using the default JDK model. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection selectedJdkModel) {
        this(selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                reflection, dependencyScope, selectedJdkModel,
                ResultRefinementSelection.defaultSelection());
    }

    /** Compatibility constructor using default k depth and JDK model. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection,
            final DependencyAnalysisScopeMode dependencyScope) {
        this(selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                reflection, dependencyScope,
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection());
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
        this(selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                WalaReflectionOptions.defaultOptions(),
                DependencyAnalysisScopeMode.defaultMode(),
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection());
    }

    /** Compatibility constructor using the default dependency scope. */
    public AnalysisRunConfiguration(
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflection) {
        this(selection, algorithm, CallGraphAlgorithm.defaultKObjDepth(),
                reflection,
                DependencyAnalysisScopeMode.defaultMode(),
                CallGraphPolicy.defaultJdkModel(algorithm),
                ResultRefinementSelection.defaultSelection());
    }
}
