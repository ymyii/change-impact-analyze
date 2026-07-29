package io.github.dependencyanalysis.preflight;

import java.util.ArrayList;
import java.util.List;

/** Ordered collection of preflight results. */
public final class PreflightReport {

    /** Results. */
    private final List<PreflightResult> results;

    /**
     * Creates a report.
     *
     * @param reportResults results
     */
    public PreflightReport(
            final List<PreflightResult>
                    reportResults) {
        results = List.copyOf(reportResults);
    }

    /** @return ordered results */
    public List<PreflightResult> getResults() {
        return results;
    }

    /** @return true when command cannot start */
    public boolean blocksCommand() {
        return results.stream().anyMatch(
                item -> item.getDecision()
                        == PreflightDecision
                        .BLOCK_COMMAND);
    }

    /**
     * Tests whether one reactor is blocked.
     *
     * @param scopeId reactor identifier
     * @return true if blocked
     */
    public boolean blocksReactor(
            final String scopeId) {
        return results.stream().anyMatch(
                item -> item.getScope()
                        == PreflightScope.REACTOR
                        && item.getScopeId()
                        .equals(scopeId)
                        && item.getDecision()
                        == PreflightDecision
                        .BLOCK_REACTOR);
    }

    /** @return true when any fallback is active */
    public boolean isDegraded() {
        return results.stream().anyMatch(
                item -> item.getDecision()
                        == PreflightDecision.DEGRADE);
    }

    /**
     * Combines reports without reordering.
     *
     * @param reports source reports
     * @return combined report
     */
    public static PreflightReport combine(
            final List<PreflightReport> reports) {
        final List<PreflightResult> combined =
                new ArrayList<>();
        for (PreflightReport report : reports) {
            combined.addAll(report.getResults());
        }
        return new PreflightReport(combined);
    }
}
