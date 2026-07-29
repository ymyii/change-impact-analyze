package io.github.dependencyanalysis.tree;

import java.util.Objects;

/** Lightweight operational issue from one reactor analysis. */
public final class TreeAnalysisIssue {

    /** Reactor identifier. */
    private final String reactorId;

    /** Non-success reactor status. */
    private final ReactorStatus status;

    /** Diagnostic reason. */
    private final String reason;

    /**
     * Creates an issue.
     *
     * @param id reactor identifier
     * @param reactorStatus status
     * @param diagnosticReason reason
     */
    public TreeAnalysisIssue(
            final String id,
            final ReactorStatus reactorStatus,
            final String diagnosticReason) {
        reactorId = Objects.requireNonNull(
                id, "id");
        status = Objects.requireNonNull(
                reactorStatus, "reactorStatus");
        if (status == ReactorStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "SUCCESS is not an analysis issue");
        }
        reason = diagnosticReason == null
                ? "" : diagnosticReason;
    }

    static TreeAnalysisIssue from(
            final ReactorReportSummary summary) {
        return new TreeAnalysisIssue(summary.getId(),
                summary.getStatus(), summary.getReason());
    }

    /** @return reactor identifier */
    public String getReactorId() {
        return reactorId;
    }

    /** @return reactor status */
    public ReactorStatus getStatus() {
        return status;
    }

    /** @return diagnostic reason */
    public String getReason() {
        return reason;
    }
}
