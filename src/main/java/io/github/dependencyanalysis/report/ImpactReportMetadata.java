package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import java.util.Objects;

/** Preflight and runtime metadata for impact reports. */
public final class ImpactReportMetadata {

    /** Preflight report. */
    private final PreflightReport preflight;

    /** Maven runtime. */
    private final MavenRuntimeDescriptor runtime;

    /**
     * Creates report metadata.
     *
     * @param preflightReport canonical preflight result
     * @param selectedRuntime Maven runtime
     */
    public ImpactReportMetadata(
            final PreflightReport preflightReport,
            final MavenRuntimeDescriptor selectedRuntime) {
        preflight = Objects.requireNonNull(
                preflightReport, "preflightReport");
        runtime = Objects.requireNonNull(
                selectedRuntime, "selectedRuntime");
    }

    /** @return preflight report */
    public PreflightReport getPreflight() {
        return preflight;
    }

    /** @return Maven runtime */
    public MavenRuntimeDescriptor getRuntime() {
        return runtime;
    }
}
