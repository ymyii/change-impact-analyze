package io.github.dependencyanalysis.preflight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Wiki: wiki/c4/components/dependency-analyzer-cli-command-control.md - 校验边界
/** Executes a preflight DAG in stable topological order. */
public final class PreflightRunner {

    /** Nanoseconds in one millisecond. */
    private static final long NANOS_PER_MILLI =
            1_000_000L;

    /**
     * Executes all checks.
     *
     * @param plan check plan
     * @param context prepared context
     * @return structured report
     */
    public PreflightReport run(
            final PreflightPlan plan,
            final PreflightContext context) {
        final List<PreflightCheck> ordered =
                sort(plan.getChecks());
        final Map<String, PreflightResult> done =
                new LinkedHashMap<>();
        for (PreflightCheck check : ordered) {
            final PreflightResult result =
                    execute(check, context, done);
            done.put(check.getCheckId(), result);
        }
        return new PreflightReport(
                new ArrayList<>(done.values()));
    }

    private PreflightResult execute(
            final PreflightCheck check,
            final PreflightContext context,
            final Map<String, PreflightResult> done) {
        final List<String> blockers =
                new ArrayList<>();
        for (String dependency
                : check.getDependsOn()) {
            final PreflightResult result =
                    done.get(dependency);
            if (result.getStatus()
                    == PreflightStatus.FAIL
                    || result.getStatus()
                    == PreflightStatus.SKIPPED) {
                blockers.add(dependency);
            }
        }
        if (!blockers.isEmpty()) {
            return new PreflightResult(
                    check, PreflightStatus.SKIPPED,
                    blockingDecision(check),
                    "Skipped because prerequisite failed",
                    String.join(",", blockers), "", 0);
        }
        final long started = System.nanoTime();
        try {
            final PreflightOutcome outcome =
                    check.execute(context);
            final long elapsed = elapsed(started);
            if (outcome.isSuccessful()) {
                return new PreflightResult(
                        check, PreflightStatus.PASS,
                        PreflightDecision.CONTINUE,
                        outcome.getSummary(),
                        outcome.getEvidence(),
                        outcome.getFallback(), elapsed);
            }
            if (check.getRequirement()
                    == PreflightRequirement.DEGRADABLE) {
                return new PreflightResult(
                        check, PreflightStatus.WARN,
                        PreflightDecision.DEGRADE,
                        outcome.getSummary(),
                        outcome.getEvidence(),
                        outcome.getFallback(), elapsed);
            }
            return new PreflightResult(
                    check, PreflightStatus.FAIL,
                    blockingDecision(check),
                    outcome.getSummary(),
                    outcome.getEvidence(),
                    outcome.getFallback(), elapsed);
        } catch (Exception exception) {
            final PreflightStatus status =
                    check.getRequirement()
                    == PreflightRequirement.DEGRADABLE
                            ? PreflightStatus.WARN
                            : PreflightStatus.FAIL;
            final PreflightDecision decision =
                    status == PreflightStatus.WARN
                            ? PreflightDecision.DEGRADE
                            : blockingDecision(check);
            return new PreflightResult(
                    check, status, decision,
                    "Check failed",
                    "exception=" + exception.getClass().getName(),
                    status == PreflightStatus.WARN
                            ? "Use reduced capability"
                            : "",
                    elapsed(started));
        }
    }

    private PreflightDecision blockingDecision(
            final PreflightCheck check) {
        if (check.getScope()
                == PreflightScope.REACTOR) {
            return PreflightDecision.BLOCK_REACTOR;
        }
        return PreflightDecision.BLOCK_COMMAND;
    }

    private long elapsed(final long started) {
        return (System.nanoTime() - started)
                / NANOS_PER_MILLI;
    }

    private List<PreflightCheck> sort(
            final List<PreflightCheck> checks) {
        final Map<String, PreflightCheck> byId =
                new LinkedHashMap<>();
        for (PreflightCheck check : checks) {
            if (byId.put(check.getCheckId(), check)
                    != null) {
                throw new IllegalArgumentException(
                        "Duplicate preflight check: "
                                + check.getCheckId());
            }
        }
        final List<PreflightCheck> ordered =
                new ArrayList<>();
        final Map<String, Integer> states =
                new HashMap<>();
        for (PreflightCheck check : checks) {
            visit(check, byId, states, ordered);
        }
        return ordered;
    }

    private void visit(
            final PreflightCheck check,
            final Map<String, PreflightCheck> byId,
            final Map<String, Integer> states,
            final List<PreflightCheck> ordered) {
        final Integer state = states.get(
                check.getCheckId());
        if (Integer.valueOf(2).equals(state)) {
            return;
        }
        if (Integer.valueOf(1).equals(state)) {
            throw new IllegalArgumentException(
                    "Preflight cycle at: "
                            + check.getCheckId());
        }
        states.put(check.getCheckId(), 1);
        for (String dependency
                : check.getDependsOn()) {
            final PreflightCheck prerequisite =
                    byId.get(dependency);
            if (prerequisite == null) {
                throw new IllegalArgumentException(
                        "Unknown preflight dependency: "
                                + dependency);
            }
            visit(prerequisite, byId,
                    states, ordered);
        }
        states.put(check.getCheckId(), 2);
        ordered.add(check);
    }
}
