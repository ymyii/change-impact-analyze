package io.github.dependencyanalysis.tree;

import java.util.Objects;

/** Immutable final console summary for one tree command run. */
final class TreeRunSummary {

    /** Terminal status. */
    private final TreeReportState status;

    /** Report disposition. */
    private final String report;

    TreeRunSummary(
            final TreeReportState commandStatus,
            final String reportValue) {
        status = Objects.requireNonNull(
                commandStatus, "commandStatus");
        report = Objects.requireNonNull(
                reportValue, "reportValue");
    }

    TreeReportState getStatus() {
        return status;
    }

    String getReport() {
        return report;
    }

}
