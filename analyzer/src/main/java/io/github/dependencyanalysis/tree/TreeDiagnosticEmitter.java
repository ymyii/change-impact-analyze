package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.preflight.PreflightConsoleRenderer;
import io.github.dependencyanalysis.preflight.PreflightReport;

import java.util.List;

/** Emits Tree command semantics through the unified Diagnostic log. */
final class TreeDiagnosticEmitter {

    /** Log. */
    private final DiagnosticLog log;

    TreeDiagnosticEmitter(final DiagnosticLog diagnosticLog) {
        log = diagnosticLog;
    }

    void debug(final String message) {
        log.debug(DiagnosticContext.of("cli", "tree"), message);
    }

    void trace(final String message) {
        log.trace(DiagnosticContext.of("cli", "tree"), message);
    }

    void preflight(final PreflightReport report) {
        log.transientLog(DiagnosticContext.of("preflight", "summary"),
                DiagnosticLevel.INFO, LogVerbosity.INFO,
                "Stage 1/3: Preflight");
        new PreflightConsoleRenderer().render(report, log);
    }

    void analysisStarted(final int totalReactors) {
        log.info(DiagnosticContext.of("analysis", "summary")
                        .with("status", "RUNNING")
                        .with("reactors", totalReactors),
                "Stage 2/3: Analysis");
    }

    void analysisSkipped(final String reason) {
        log.error(DiagnosticContext.of("analysis", "summary")
                        .with("status", "SKIPPED"),
                "Stage 2/3: Analysis; " + oneLine(reason));
    }

    void reactorStarted(
            final int index,
            final int total,
            final String reactorId) {
        log.info(reactorContext(index, total, reactorId)
                        .with("status", "RUNNING"),
                "Maven collection started");
    }

    void reactorCompleted(
            final int index,
            final int total,
            final ReactorTreeResult result) {
        final DiagnosticContext context = reactorContext(
                index, total, result.getReactor().getId())
                .with("status", result.getStatus())
                .with("modules", result.getModules().size());
        emit(level(result.getStatus()), context, "Maven collection completed");
    }

    void analysisCompleted(final List<TreeAnalysisIssue> issues) {
        final DiagnosticLevel summaryLevel = issues.isEmpty()
                ? DiagnosticLevel.INFO : DiagnosticLevel.WARN;
        emit(summaryLevel, DiagnosticContext.of("analysis", "summary")
                        .with("status", issues.isEmpty()
                                ? "SUCCESS" : "COMPLETED_WITH_ISSUES")
                        .with("issues", issues.size()),
                "Analysis completed");
        for (int index = 0; index < issues.size(); index++) {
            final TreeAnalysisIssue issue = issues.get(index);
            emit(level(issue.getStatus()),
                    DiagnosticContext.of("analysis", "issue")
                            .with("reactor", issue.getReactorId())
                            .with("status", issue.getStatus())
                            .with("issue", index + 1),
                    oneLine(issue.getReason()));
        }
    }

    void summary(final TreeRunSummary summary) {
        emit(level(summary.getStatus()),
                DiagnosticContext.of("summary", "result")
                        .with("status", summary.getStatus())
                        .with("report", summary.getReport()),
                "Stage 3/3: Summary");
    }

    private DiagnosticContext reactorContext(
            final int index,
            final int total,
            final String reactorId) {
        return DiagnosticContext.of("analysis", "reactor")
                .with("reactor", reactorId)
                .with("progress", index + "/" + total);
    }

    private DiagnosticLevel level(final ReactorStatus status) {
        return switch (status) {
            case SUCCESS -> DiagnosticLevel.INFO;
            case DEGRADED -> DiagnosticLevel.WARN;
            case FAILED -> DiagnosticLevel.ERROR;
        };
    }

    private DiagnosticLevel level(final TreeReportState status) {
        return switch (status) {
            case SUCCESS -> DiagnosticLevel.INFO;
            case RUNNING, COMPLETED_WITH_ISSUES -> DiagnosticLevel.WARN;
            case FAILED -> DiagnosticLevel.ERROR;
        };
    }

    private void emit(
            final DiagnosticLevel level,
            final DiagnosticContext context,
            final String message) {
        switch (level) {
            case INFO -> log.info(context, message);
            case WARN -> log.warn(context, message);
            case ERROR -> log.error(context, message);
            case DEBUG -> log.debug(context, message);
            case TRACE -> log.trace(context, message);
            default -> throw new IllegalArgumentException(
                    "Unsupported diagnostic level: " + level);
        }
    }

    private String oneLine(final String value) {
        if (value == null || value.isBlank()) {
            return "unspecified";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
