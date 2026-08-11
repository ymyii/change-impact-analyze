package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete per-module analysis output consumed by the HTML publisher. */
public final class AnalysisRunResult {

    /** Analysis mode. */
    private final AnalysisMode mode;

    /** Overall status. */
    private final AnalysisStatus status;

    /** Dependency changes. */
    private final List<DependencyChange> dependencyChanges;

    /** Per-module results. */
    private final List<ModuleAnalysisResult> moduleResults;

    /** Worker configuration. */
    private final AnalysisConcurrency concurrency;

    /** Stable stage elapsed metrics. */
    private final Map<String, Long> stageElapsedMillis;

    /** User-selected PROJECT entrypoint boundary. */
    private final EntrypointSelection entrypointSelection;

    /** Command-wide Call Graph algorithm. */
    private final CallGraphAlgorithm callGraphAlgorithm;

    /** Command-wide WALA ReflectionOptions. */
    private final WalaReflectionOptions reflectionOptions;

    /** Requested dependency method-body scope. */
    private final DependencyAnalysisScopeMode dependencyAnalysisScope;

    /** Command-wide JDK Method Model selection. */
    private final JdkModelSelection jdkModel;

    /**
     * Creates a completed run result.
     *
     * @param analysisMode selected mode
     * @param analysisStatus overall status
     * @param changes dependency changes
     * @param modules per-module results
     * @param workers worker configuration
     * @param stageMetrics elapsed metrics
     * @param selection user-selected PROJECT entrypoint boundary
     */
    public AnalysisRunResult(
            final AnalysisMode analysisMode,
            final AnalysisStatus analysisStatus,
            final List<DependencyChange> changes,
            final List<ModuleAnalysisResult> modules,
            final AnalysisConcurrency workers,
            final Map<String, Long> stageMetrics,
            final EntrypointSelection selection) {
        this(analysisMode, analysisStatus, changes, modules, workers,
                stageMetrics, new AnalysisRunConfiguration(
                        selection, CallGraphAlgorithm.defaultAlgorithm()));
    }

    /**
     * Creates a completed run result with an explicit Call Graph algorithm.
     *
     * @param analysisMode selected mode
     * @param analysisStatus overall status
     * @param changes dependency changes
     * @param modules per-module results
     * @param workers worker configuration
     * @param stageMetrics elapsed metrics
     * @param configuration command-wide analysis configuration
     */
    public AnalysisRunResult(
            final AnalysisMode analysisMode,
            final AnalysisStatus analysisStatus,
            final List<DependencyChange> changes,
            final List<ModuleAnalysisResult> modules,
            final AnalysisConcurrency workers,
            final Map<String, Long> stageMetrics,
            final AnalysisRunConfiguration configuration) {
        mode = analysisMode;
        status = analysisStatus;
        dependencyChanges = Collections.unmodifiableList(
                new ArrayList<>(changes));
        moduleResults = Collections.unmodifiableList(
                new ArrayList<>(modules));
        concurrency = java.util.Objects.requireNonNull(
                workers, "concurrency");
        stageElapsedMillis = Collections.unmodifiableMap(
                new LinkedHashMap<>(stageMetrics));
        final AnalysisRunConfiguration settings =
                java.util.Objects.requireNonNull(
                        configuration, "configuration");
        entrypointSelection = java.util.Objects.requireNonNull(
                settings.entrypointSelection(), "entrypointSelection");
        callGraphAlgorithm = java.util.Objects.requireNonNull(
                settings.callGraphAlgorithm(), "callGraphAlgorithm");
        reflectionOptions = java.util.Objects.requireNonNull(
                settings.reflectionOptions(), "reflectionOptions");
        dependencyAnalysisScope = java.util.Objects.requireNonNull(
                settings.dependencyAnalysisScope(),
                "dependencyAnalysisScope");
        jdkModel = java.util.Objects.requireNonNull(
                settings.jdkModel(), "jdkModel");
    }

    /** @return analysis mode */
    public AnalysisMode getMode() {
        return mode;
    }

    /** @return overall status */
    public AnalysisStatus getStatus() {
        return status;
    }

    /** @return dependency changes */
    public List<DependencyChange> getDependencyChanges() {
        return dependencyChanges;
    }

    /** @return per-module results */
    public List<ModuleAnalysisResult> getModuleResults() {
        return moduleResults;
    }

    /** @return configured module parallelism */
    public int getConfiguredParallelism() {
        return concurrency.configuredAnalysisParallelism();
    }

    /** @return actual module parallelism */
    public int getActualParallelism() {
        return concurrency.actualModuleParallelism();
    }

    /** @return configured JAR diff workers */
    public int getConfiguredJarDiffWorkers() {
        return concurrency.configuredAnalysisParallelism();
    }

    /** @return actual JAR diff workers */
    public int getActualJarDiffWorkers() {
        return concurrency.actualJarDiffWorkers();
    }

    /** @return configured safe-stage analysis parallelism */
    public int getConfiguredAnalysisParallelism() {
        return concurrency.configuredAnalysisParallelism();
    }

    /** @return actual code evidence workers */
    public int getActualDecompileWorkers() {
        return concurrency.actualDecompileWorkers();
    }

    /** @return configured PROJECT entrypoint boundary */
    public EntrypointSelection getEntrypointSelection() {
        return entrypointSelection;
    }

    /** @return command-wide Call Graph algorithm */
    public CallGraphAlgorithm getCallGraphAlgorithm() {
        return callGraphAlgorithm;
    }

    /** @return command-wide WALA ReflectionOptions */
    public WalaReflectionOptions getReflectionOptions() {
        return reflectionOptions;
    }

    /** @return requested dependency method-body scope */
    public DependencyAnalysisScopeMode getDependencyAnalysisScope() {
        return dependencyAnalysisScope;
    }

    /** @return command-wide JDK Method Model selection */
    public JdkModelSelection getJdkModel() {
        return jdkModel;
    }

    /** @return stage elapsed metrics */
    public Map<String, Long> getStageElapsedMillis() {
        return stageElapsedMillis;
    }
}
