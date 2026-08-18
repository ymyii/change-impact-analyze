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
        log.info(DiagnosticContext.of("analysis", "summary"),
                "Stage 2/3: Analysis; status=RUNNING; reactors="
                        + totalReactors);
    }

    void analysisSkipped(final String reason) {
        log.error(DiagnosticContext.of("analysis", "summary"),
                "Stage 2/3: Analysis; status=SKIPPED; reason="
                        + oneLine(reason));
    }

    void reactorStarted(
            final int index,
            final int total,
            final String reactorId) {
        log.info(reactorContext(reactorId),
                "Maven collection started; progress=" + index + "/" + total
                        + "; status=RUNNING");
    }

    void reactorCompleted(
            final int index,
            final int total,
            final ReactorTreeResult result) {
        final DiagnosticContext context = reactorContext(
                result.getReactor().getId());
        emit(level(result.getStatus()), context,
                "Maven collection completed; progress=" + index + "/" + total
                        + "; status=" + result.getStatus()
                        + "; modules=" + result.getModules().size());
        final long incompleteModules = result.getModules().stream()
                .filter(module -> !module.getClasspathIssues().isEmpty())
                .count();
        final long incompleteIssues = result.getModules().stream()
                .mapToLong(module -> module.getClasspathIssues().size())
                .sum();
        if (incompleteIssues > 0) {
            log.warn(DiagnosticContext.of("analysis",
                            "classpath-incomplete")
                            .with("reactor",
                                    result.getReactor().getId()),
                    "Classpath incomplete; modules=" + incompleteModules
                            + "; issues=" + incompleteIssues);
        }
    }

    void analysisCompleted(final List<TreeAnalysisIssue> issues) {
        final DiagnosticLevel summaryLevel = issues.isEmpty()
                ? DiagnosticLevel.INFO : DiagnosticLevel.WARN;
        emit(summaryLevel, DiagnosticContext.of("analysis", "summary"),
                "Analysis completed; status=" + (issues.isEmpty()
                        ? "SUCCESS" : "COMPLETED_WITH_ISSUES")
                        + "; issues=" + issues.size());
        for (int index = 0; index < issues.size(); index++) {
            final TreeAnalysisIssue issue = issues.get(index);
            emit(level(issue.getStatus()),
                    DiagnosticContext.of("analysis", "issue")
                            .with("reactor", issue.getReactorId()),
                    "Analysis issue; issue=" + (index + 1)
                            + "; status=" + issue.getStatus()
                            + "; reason=" + oneLine(issue.getReason()));
        }
    }

    void summary(final TreeRunSummary summary) {
        emit(level(summary.getStatus()),
                DiagnosticContext.of("summary", "result"),
                "Stage 3/3: Summary; status=" + summary.getStatus()
                        + "; report=" + summary.getReport());
    }

    private DiagnosticContext reactorContext(final String reactorId) {
        return DiagnosticContext.of("analysis", "reactor")
                .with("reactor", reactorId);
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
