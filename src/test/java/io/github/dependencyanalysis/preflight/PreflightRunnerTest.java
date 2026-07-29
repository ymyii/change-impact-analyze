package io.github.dependencyanalysis.preflight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Preflight DAG and decision tests. */
class PreflightRunnerTest {

    @Test
    void executesStableTopologicalOrder() {
        final PreflightReport report = run(List.of(
                check("last", List.of("first"),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.pass("ok", "")),
                check("first", List.of(),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.pass("ok", ""))));

        assertThat(report.getResults())
                .extracting(PreflightResult::getCheckId)
                .containsExactly("first", "last");
    }

    @Test
    void requiredFailureSkipsDependentCheck() {
        final PreflightReport report = run(List.of(
                check("failed", List.of(),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.fail(
                                "bad", "evidence", "")),
                check("skipped", List.of("failed"),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.pass("ok", ""))));

        assertThat(report.blocksCommand()).isTrue();
        assertThat(report.getResults().get(1)
                .getStatus()).isEqualTo(
                PreflightStatus.SKIPPED);
        assertThat(report.getResults().get(1)
                .getEvidence()).contains("failed");
    }

    @Test
    void degradableFailureContinuesWithFallback() {
        final PreflightReport report = run(List.of(
                check("verbose", List.of(),
                        PreflightRequirement.DEGRADABLE,
                        PreflightScope.REACTOR,
                        PreflightOutcome.fail(
                                "limited", "plugin",
                                "resolved tree"))));

        assertThat(report.isDegraded()).isTrue();
        assertThat(report.blocksCommand()).isFalse();
        assertThat(report.getResults().get(0)
                .getDecision()).isEqualTo(
                PreflightDecision.DEGRADE);
    }

    @Test
    void rejectsCycles() {
        assertThatThrownBy(() -> run(List.of(
                check("a", List.of("b"),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.pass("", "")),
                check("b", List.of("a"),
                        PreflightRequirement.REQUIRED,
                        PreflightScope.COMMAND,
                        PreflightOutcome.pass("", "")))))
                .isInstanceOf(
                        IllegalArgumentException.class);
    }

    private PreflightReport run(
            final List<PreflightCheck> checks) {
        try (PreflightContext context =
                     new PreflightContext()) {
            return new PreflightRunner().run(
                    new PreflightPlan(checks), context);
        }
    }

    private PreflightCheck check(
            final String id,
            final List<String> dependencies,
            final PreflightRequirement requirement,
            final PreflightScope scope,
            final PreflightOutcome outcome) {
        return new SimplePreflightCheck(
                id, "test", scope, "scope",
                requirement, dependencies,
                context -> outcome);
    }
}
