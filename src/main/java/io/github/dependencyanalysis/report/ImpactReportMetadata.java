package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.MethodBodyEvidence;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Preflight and runtime metadata for impact reports. */
public final class ImpactReportMetadata {

    /** Preflight report. */
    private final PreflightReport preflight;

    /** Maven runtime. */
    private final MavenRuntimeDescriptor runtime;

    /** Method body evidence. */
    private final List<MethodBodyEvidence> methodBodyEvidence;

    /**
     * Creates report metadata.
     *
     * @param preflightReport canonical preflight result
     * @param selectedRuntime Maven runtime
     */
    public ImpactReportMetadata(
            final PreflightReport preflightReport,
            final MavenRuntimeDescriptor selectedRuntime) {
        this(preflightReport, selectedRuntime,
                Collections.emptyList());
    }

    /**
     * Creates report metadata with method body evidence.
     *
     * @param preflightReport canonical preflight result
     * @param selectedRuntime Maven runtime
     * @param evidence method body evidence
     */
    public ImpactReportMetadata(
            final PreflightReport preflightReport,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<MethodBodyEvidence> evidence) {
        preflight = Objects.requireNonNull(
                preflightReport, "preflightReport");
        runtime = Objects.requireNonNull(
                selectedRuntime, "selectedRuntime");
        methodBodyEvidence = List.copyOf(
                Objects.requireNonNull(evidence, "evidence"));
    }

    /** @return preflight report */
    public PreflightReport getPreflight() {
        return preflight;
    }

    /** @return Maven runtime */
    public MavenRuntimeDescriptor getRuntime() {
        return runtime;
    }

    /** @return method body evidence */
    public List<MethodBodyEvidence> getMethodBodyEvidence() {
        return methodBodyEvidence;
    }
}
