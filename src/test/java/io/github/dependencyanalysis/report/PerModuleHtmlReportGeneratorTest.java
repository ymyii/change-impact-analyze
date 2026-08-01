package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.AnalysisMode;
import io.github.dependencyanalysis.impact.AnalysisConcurrency;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.AnalysisStatus;
import io.github.dependencyanalysis.impact.ModuleAnalysisReason;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.ModuleAnalysisStatus;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests HTML-only per-Module report publication. */
class PerModuleHtmlReportGeneratorTest {

    /** Target JDK major. */
    private static final int TARGET_MAJOR = 8;

    /** Example query elapsed metric. */
    private static final long QUERY_ELAPSED_MILLIS = 4L;

    /** Temporary output directory. */
    @TempDir
    private Path temporary;

    @Test
    void writesBoundariesAndReplacesOwnedModuleDirectory()
            throws Exception {
        final Path output = temporary.resolve("impact.html");
        final Path owned = temporary.resolve("impact-modules");
        Files.createDirectories(owned);
        Files.writeString(owned.resolve("stale.html"), "stale");
        Files.writeString(output, "old");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "example", "app", "jar", "1"), Path.of("app")),
                ModulePresence.BOTH, temporary.resolve("classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .status(ModuleAnalysisStatus.SUCCESS,
                                ModuleAnalysisReason.NONE, "complete")
                        .stageElapsedMillis(Map.of(
                                "call-graph-query", QUERY_ELAPSED_MILLIS))
                        .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS,
                List.of(), List.of(module),
                new AnalysisConcurrency(2, 1, 4, 1),
                Map.of("module-analysis", 10L));

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), java(), output);

        final String index = Files.readString(output);
        assertThat(index).contains("Analysis Model Boundaries")
                .contains("algorithm=vanilla-0-1-cfa")
                .contains("configured module parallelism</th><td>2")
                .contains("SSA PROVEN_EQUIVALENT / DIFFERENT / UNKNOWN")
                .contains("example:app:jar@app");
        assertThat(owned.resolve("stale.html")).doesNotExist();
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(1);
            assertThat(Files.readString(values.get(0)))
                    .contains("Stage Metrics")
                    .contains("call-graph-query")
                    .contains("Dependency Upgrades")
                    .contains("Module Diagnostics")
                    .contains("在声明的analysis model内未发现Impact Path");
        }
    }

    private MavenRuntimeDescriptor maven() {
        return new MavenRuntimeDescriptor(
                MavenRuntimeSource.USER_CONFIGURED,
                Path.of("mvn"), MavenVersion.parse("3.9.9"),
                null, temporary, "");
    }

    private JavaRuntimeDescriptor java() {
        return new JavaRuntimeDescriptor(temporary, temporary,
                "1.8.0", TARGET_MAJOR, List.of(), List.of());
    }
}
