package io.github.dependencyanalysis.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration checks for final HTML Report usability validation. */
class HtmlReportUsabilityVerifierIT {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void acceptsAdjacentCssClosingBraces() throws IOException {
        final Path report = writeReport(
                "<style>@media(max-width:800px){body{margin:0}}</style>");

        HtmlReportUsabilityVerifier.verifyImpact(report);
    }

    @Test
    void rejectsUnexpandedTemplateExpression() throws IOException {
        final Path report = writeReport("<main>{{report.title}}</main>");

        assertThatThrownBy(() -> HtmlReportUsabilityVerifier.verifyImpact(report))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unexpanded template marker");
    }

    private Path writeReport(final String body) throws IOException {
        final Path report = temporaryDirectory.resolve("impact.html");
        Files.writeString(report,
                "<!DOCTYPE html><html><head><title>Impact</title></head>"
                        + "<body>" + body + "</body></html>",
                StandardCharsets.UTF_8);
        return report;
    }
}
