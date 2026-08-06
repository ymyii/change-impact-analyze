package io.github.dependencyanalysis.preflight;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

/** Emits the canonical Preflight result through the unified Diagnostic log. */
public final class PreflightConsoleRenderer {

    /**
     * Emits ordered checks as transient Console diagnostics.
     *
     * @param report Preflight report
     * @param log command log
     */
    public void render(
            final PreflightReport report,
            final DiagnosticLog log) {
        log.transientLog(DiagnosticContext.of("preflight", "summary"),
                report.blocksCommand()
                        ? DiagnosticLevel.ERROR : DiagnosticLevel.INFO,
                LogVerbosity.INFO,
                "Preflight checks=" + report.getResults().size()
                        + "; status=" + (report.blocksCommand()
                        ? "BLOCKED" : "READY"));
        for (PreflightResult result : report.getResults()) {
            final DiagnosticContext context = DiagnosticContext.of(
                    "preflight", "check")
                    .with("check", result.getCheckId());
            final DiagnosticLevel level = level(result.getStatus());
            log.transientLog(context, level, LogVerbosity.INFO,
                    result.getSummary()
                            + "; command=" + result.getCommand()
                            + "; scope=" + result.getScope()
                            + "; scopeId=" + result.getScopeId()
                            + "; status=" + result.getStatus()
                            + "; decision=" + result.getDecision()
                            + "; elapsedMs=" + result.getElapsedMillis());
            if (!result.getEvidence().isBlank()) {
                log.transientLog(context.withSubstage("evidence"), level,
                        LogVerbosity.INFO, result.getEvidence());
            }
            if (!result.getFallback().isBlank()) {
                log.transientLog(context.withSubstage("fallback"), level,
                        LogVerbosity.INFO, result.getFallback());
            }
        }
    }

    private DiagnosticLevel level(final PreflightStatus status) {
        return switch (status) {
            case PASS -> DiagnosticLevel.INFO;
            case WARN, SKIPPED -> DiagnosticLevel.WARN;
            case FAIL -> DiagnosticLevel.ERROR;
        };
    }
}
