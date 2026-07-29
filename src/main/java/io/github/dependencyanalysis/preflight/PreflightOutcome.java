package io.github.dependencyanalysis.preflight;

import java.util.Objects;

/** Raw result returned by an atomic check. */
public final class PreflightOutcome {

    /** Whether the requested capability is available. */
    private final boolean successful;

    /** Human-readable summary. */
    private final String summary;

    /** Stable evidence. */
    private final String evidence;

    /** Fallback description. */
    private final String fallback;

    private PreflightOutcome(
            final boolean success,
            final String resultSummary,
            final String resultEvidence,
            final String resultFallback) {
        successful = success;
        summary = Objects.requireNonNull(
                resultSummary, "summary");
        evidence = Objects.requireNonNull(
                resultEvidence, "evidence");
        fallback = Objects.requireNonNull(
                resultFallback, "fallback");
    }

    /**
     * Creates a successful outcome.
     *
     * @param summary summary
     * @param evidence evidence
     * @return outcome
     */
    public static PreflightOutcome pass(
            final String summary,
            final String evidence) {
        return new PreflightOutcome(true,
                summary, evidence, "");
    }

    /**
     * Creates a failed outcome.
     *
     * @param summary summary
     * @param evidence evidence
     * @param fallback fallback for degradable checks
     * @return outcome
     */
    public static PreflightOutcome fail(
            final String summary,
            final String evidence,
            final String fallback) {
        return new PreflightOutcome(false,
                summary, evidence, fallback);
    }

    /** @return success flag */
    public boolean isSuccessful() {
        return successful;
    }

    /** @return summary */
    public String getSummary() {
        return summary;
    }

    /** @return evidence */
    public String getEvidence() {
        return evidence;
    }

    /** @return fallback */
    public String getFallback() {
        return fallback;
    }
}
