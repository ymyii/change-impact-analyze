package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.DependencyChange;

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

    /**
     * Creates a completed run result.
     *
     * @param analysisMode selected mode
     * @param analysisStatus overall status
     * @param changes dependency changes
     * @param modules per-module results
     * @param workers worker configuration
     * @param stageMetrics elapsed metrics
     */
    public AnalysisRunResult(
            final AnalysisMode analysisMode,
            final AnalysisStatus analysisStatus,
            final List<DependencyChange> changes,
            final List<ModuleAnalysisResult> modules,
            final AnalysisConcurrency workers,
            final Map<String, Long> stageMetrics) {
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
        return concurrency.configuredModuleParallelism();
    }

    /** @return actual module parallelism */
    public int getActualParallelism() {
        return concurrency.actualModuleParallelism();
    }

    /** @return configured JAR diff workers */
    public int getConfiguredJarDiffWorkers() {
        return concurrency.configuredJarDiffWorkers();
    }

    /** @return actual JAR diff workers */
    public int getActualJarDiffWorkers() {
        return concurrency.actualJarDiffWorkers();
    }

    /** @return stage elapsed metrics */
    public Map<String, Long> getStageElapsedMillis() {
        return stageElapsedMillis;
    }
}
