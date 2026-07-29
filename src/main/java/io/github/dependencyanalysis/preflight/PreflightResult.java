package io.github.dependencyanalysis.preflight;

import java.util.List;
import java.util.Objects;

/** Immutable structured check result. */
public final class PreflightResult {

    /** Check identifier. */
    private final String checkId;

    /** Command name. */
    private final String command;

    /** Scope. */
    private final PreflightScope scope;

    /** Scope identifier. */
    private final String scopeId;

    /** Requirement. */
    private final PreflightRequirement requirement;

    /** Status. */
    private final PreflightStatus status;

    /** Decision. */
    private final PreflightDecision decision;

    /** Summary. */
    private final String summary;

    /** Evidence. */
    private final String evidence;

    /** Fallback. */
    private final String fallback;

    /** Dependencies. */
    private final List<String> dependsOn;

    /** Duration. */
    private final long elapsedMillis;

    /**
     * Creates a result.
     *
     * @param check source check
     * @param resultStatus status
     * @param resultDecision decision
     * @param resultSummary summary
     * @param resultEvidence evidence
     * @param resultFallback fallback
     * @param elapsed duration
     */
    public PreflightResult(
            final PreflightCheck check,
            final PreflightStatus resultStatus,
            final PreflightDecision resultDecision,
            final String resultSummary,
            final String resultEvidence,
            final String resultFallback,
            final long elapsed) {
        checkId = check.getCheckId();
        command = check.getCommand();
        scope = check.getScope();
        scopeId = check.getScopeId();
        requirement = check.getRequirement();
        status = Objects.requireNonNull(
                resultStatus, "status");
        decision = Objects.requireNonNull(
                resultDecision, "decision");
        summary = resultSummary;
        evidence = resultEvidence;
        fallback = resultFallback;
        dependsOn = List.copyOf(
                check.getDependsOn());
        elapsedMillis = elapsed;
    }

    /** @return check identifier */
    public String getCheckId() {
        return checkId;
    }

    /** @return command name */
    public String getCommand() {
        return command;
    }

    /** @return scope */
    public PreflightScope getScope() {
        return scope;
    }

    /** @return scope identifier */
    public String getScopeId() {
        return scopeId;
    }

    /** @return requirement */
    public PreflightRequirement getRequirement() {
        return requirement;
    }

    /** @return status */
    public PreflightStatus getStatus() {
        return status;
    }

    /** @return decision */
    public PreflightDecision getDecision() {
        return decision;
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

    /** @return dependencies */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /** @return duration */
    public long getElapsedMillis() {
        return elapsedMillis;
    }
}
