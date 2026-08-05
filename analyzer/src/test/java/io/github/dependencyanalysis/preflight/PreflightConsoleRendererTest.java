package io.github.dependencyanalysis.preflight;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests structured Preflight Console emission. */
class PreflightConsoleRendererTest {

    @Test
    void mapsStatusesAndKeepsExpandedLinesTransient() {
        final PreflightReport report;
        try (PreflightContext context = new PreflightContext()) {
            report = new PreflightRunner().run(new PreflightPlan(List.of(
                    check("pass", List.of(), PreflightRequirement.REQUIRED,
                            PreflightOutcome.pass("passed", "pass evidence")),
                    check("warn", List.of(), PreflightRequirement.DEGRADABLE,
                            PreflightOutcome.fail("degraded", "warn evidence",
                                    "fallback used")),
                    check("fail", List.of(), PreflightRequirement.REQUIRED,
                            PreflightOutcome.fail("failed", "fail evidence",
                                    "")),
                    check("skipped", List.of("fail"),
                            PreflightRequirement.REQUIRED,
                            PreflightOutcome.pass("unused", "")))), context);
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.INFO);

        new PreflightConsoleRenderer().render(report, log);

        final String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains("[INFO][preflight][check]"
                        + "[command=test;check=pass;")
                .contains("status=PASS;decision=CONTINUE")
                .contains("[WARN][preflight][check]"
                        + "[command=test;check=warn;")
                .contains("status=WARN;decision=DEGRADE")
                .contains("[ERROR][preflight][check]"
                        + "[command=test;check=fail;")
                .contains("status=FAIL;decision=BLOCK_COMMAND")
                .contains("[WARN][preflight][check]"
                        + "[command=test;check=skipped;")
                .contains("status=SKIPPED;decision=BLOCK_COMMAND")
                .contains("[WARN][preflight][fallback]", "fallback used");
        assertThat(output.lines()).allMatch(line -> line.matches(
                "^\\[[^]]+]\\[(INFO|WARN|ERROR)]\\[preflight]"
                        + "\\[[^]]+]\\[[^]]+] .*$"));
        assertThat(log.getEvents()).isEmpty();
    }

    private PreflightCheck check(
            final String id,
            final List<String> dependencies,
            final PreflightRequirement requirement,
            final PreflightOutcome outcome) {
        return new SimplePreflightCheck(id, "test",
                PreflightScope.COMMAND, "scope", requirement,
                dependencies, context -> outcome);
    }
}
