package io.github.dependencyanalysis.diagnostic;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the stable five-segment Diagnostic format. */
class DiagnosticLogFormatterTest {

    @Test
    void formatsCanonicalIdentityAndEscapesDelimiters() {
        final DiagnosticEvent event = new DiagnosticEvent.Builder()
                .timestamp(OffsetDateTime.parse(
                        "2026-08-05T14:30:01.123+08:00"))
                .stage("module[analysis]")
                .substage("call-graph")
                .attribute("module", "g:a=1;path\\value")
                .attribute("check", "jdk8")
                .attribute("artifact", "g:a:1")
                .level(DiagnosticLevel.TRACE)
                .message("snapshot")
                .build();

        assertThat(new DiagnosticLogFormatter().format(event)).isEqualTo(
                "[2026-08-05T14:30:01.123+08:00]"
                        + "[TRACE][module\\[analysis\\]][call-graph]"
                        + "[check=jdk8;module=g:a\\=1\\;path\\\\value;"
                        + "artifact=g:a:1] snapshot");
    }

    @Test
    void usesDashForMissingStageSubstageAndAttributes() {
        final DiagnosticEvent event = new DiagnosticEvent.Builder()
                .timestamp(OffsetDateTime.parse(
                        "2026-08-05T14:30:01.123+08:00"))
                .level(DiagnosticLevel.INFO)
                .message("empty")
                .build();

        assertThat(new DiagnosticLogFormatter().format(event))
                .isEqualTo("[2026-08-05T14:30:01.123+08:00]"
                        + "[INFO][-][-][-] empty");
    }
}
