package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
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
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.ChangePointDisposition;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.JarDiffFailure;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
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

    /** Pages per analyzed Module. */
    private static final int MODULE_PAGE_COUNT = 3;

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
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyChange dependencyChange = new DependencyChange(
                ChangeType.VERSION_CHANGED, oldArtifact, newArtifact,
                DependencyScope.COMPILE, "example:app:jar:1");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact,
                temporary.resolve("library-1.jar"),
                temporary.resolve("library-2.jar"));
        final BoundChangePoint bound = new BoundChangePoint(upgrade,
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_REMOVED,
                        "example/library/Api", "removed", "()V",
                        null, null));
        final JarDiffFailure failure = new JarDiffFailure(
                upgrade, "fixture comparison failure");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId,
                ModulePresence.BOTH, temporary.resolve("classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(dependencyChange),
                        List.of(bound), List.of(failure)));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .status(ModuleAnalysisStatus.SUCCESS,
                                ModuleAnalysisReason.NONE,
                                "complete <unsafe>")
                        .stageElapsedMillis(Map.of(
                                "call-graph-query", QUERY_ELAPSED_MILLIS))
                        .dispositions(Map.of(bound,
                                ChangePointDisposition.NO_PROJECT_PATH))
                        .build();
        final ModuleAnalysisUnit skippedUnit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "example", "new-module", "jar", "1"),
                        Path.of("new-module")),
                ModulePresence.TARGET_ONLY,
                temporary.resolve("new-module-classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final ModuleAnalysisResult skipped =
                new ModuleAnalysisResult.Builder(skippedUnit)
                        .status(ModuleAnalysisStatus.SKIPPED,
                                ModuleAnalysisReason.SKIPPED_NO_BASELINE,
                                "target only")
                        .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS,
                List.of(dependencyChange), List.of(module, skipped),
                new AnalysisConcurrency(2, 1, 4, 1),
                Map.of("module-analysis", 10L));

        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config"), List.of(), null);
        final DiagnosticEvent exact = new DiagnosticEvent.Builder()
                .stage("module-analysis").task("module")
                .module(moduleId.stableKey()).level(DiagnosticLevel.INFO)
                .message("exact module event").build();
        final DiagnosticEvent misleading = new DiagnosticEvent.Builder()
                .stage("other").module("different-module")
                .level(DiagnosticLevel.INFO)
                .message(moduleId.stableKey() + " substring event").build();
        new PerModuleHtmlReportGenerator().generate(run,
                List.of(exact, misleading),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final String index = Files.readString(output);
        assertThat(index).contains("How to read this report")
                .contains("Analysis scope and limitations")
                .contains("Terminology")
                .contains("Maven Dependency Plugin")
                .contains("embedded 3.6.1")
                .contains("example:app:jar@app")
                .contains("example:new-module:jar@new-module")
                .contains("Skipped");
        assertThat(owned.resolve("stale.html")).doesNotExist();
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            final Path moduleIndex = values.stream()
                    .filter(path -> !path.getFileName().toString()
                            .contains("-impact"))
                    .filter(path -> !path.getFileName().toString()
                            .contains("-changes"))
                    .findFirst().orElseThrow();
            final Path impact = values.stream()
                    .filter(path -> path.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow();
            final Path changes = values.stream()
                    .filter(path -> path.getFileName().toString()
                            .contains("-changes"))
                    .findFirst().orElseThrow();
            assertThat(Files.readString(moduleIndex))
                    .contains("Module Diagnostics")
                    .contains("Stage elapsed time")
                    .contains("embedded 3.6.1")
                    .contains("Affected Call Chains")
                    .contains("Dependency Changes")
                    .contains("Technical details")
                    .contains("aria-label=\"Table of contents\"")
                    .contains("exact module event")
                    .contains("complete &lt;unsafe&gt;")
                    .doesNotContain("<unsafe>")
                    .doesNotContain("substring event");
            assertThat(Files.readString(impact))
                    .contains("No affected call chain was found within the "
                            + "documented analysis scope.")
                    .contains("Class structure references");
            assertThat(Files.readString(changes))
                    .contains("example:library 1 → 2")
                    .contains("Method removed")
                    .contains("No application method was found")
                    .contains("fixture comparison failure")
                    .contains("Old path")
                    .contains("Raw ChangePointKind")
                    .contains("Module Index");
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
