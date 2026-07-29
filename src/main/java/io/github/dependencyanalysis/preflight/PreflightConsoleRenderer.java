package io.github.dependencyanalysis.preflight;

import java.io.PrintStream;

/** Prints the canonical preflight result. */
public final class PreflightConsoleRenderer {

    /**
     * Prints ordered checks.
     *
     * @param report preflight report
     * @param output destination
     */
    public void render(
            final PreflightReport report,
            final PrintStream output) {
        output.println("Preflight:");
        for (PreflightResult result
                : report.getResults()) {
            output.printf(
                    "[%s] %s %s/%s: %s%n",
                    result.getStatus(),
                    result.getCheckId(),
                    result.getScope(),
                    result.getScopeId(),
                    result.getSummary());
            if (!result.getEvidence().isBlank()) {
                output.println("  evidence: "
                        + result.getEvidence());
            }
            if (!result.getFallback().isBlank()) {
                output.println("  fallback: "
                        + result.getFallback());
            }
        }
    }
}
