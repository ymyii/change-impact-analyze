package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.impact.AnalysisMode;
import io.github.dependencyanalysis.impact.AnalysisConcurrency;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.AnalysisRunConfiguration;
import io.github.dependencyanalysis.impact.AnalysisStatus;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.callgraph.scope.ScopeValidationWarning;
import io.github.dependencyanalysis.impact.ChangePointTerminal;
import io.github.dependencyanalysis.impact.AccessDecision;
import io.github.dependencyanalysis.impact.AccessDecisionReason;
import io.github.dependencyanalysis.impact.AccessReferenceEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonStatus;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactPathRootKind;
import io.github.dependencyanalysis.impact.refinement.ssa.MethodEquivalenceResult;
import io.github.dependencyanalysis.impact.refinement.ssa.MethodEquivalenceStatus;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.UnifiedDiffHunk;
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
import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.JarDiffFailure;
import io.github.dependencyanalysis.impact.EvidenceKind;
import io.github.dependencyanalysis.impact.EvidenceLocation;
import io.github.dependencyanalysis.impact.EvidenceMechanism;
import io.github.dependencyanalysis.impact.ReferenceEvidence;
import io.github.dependencyanalysis.impact.ReferenceTarget;
import io.github.dependencyanalysis.impact.StructuralReference;
import io.github.dependencyanalysis.impact.StructuralReferenceKind;
import io.github.dependencyanalysis.impact.StructuralReferencePath;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests HTML-only per-Module report publication. */
class PerModuleHtmlReportGeneratorTest {

    /** Target JDK major. */
    private static final int TARGET_MAJOR = 8;

    /** Example query elapsed metric. */
    private static final long QUERY_ELAPSED_MILLIS = 4L;

    /** Pages per analyzed Module. */
    private static final int MODULE_PAGE_COUNT = 2;

    /** Dense changed member fixture size. */
    private static final int DENSE_MEMBER_COUNT = 2_501;

    /** Unicode line separator protected in embedded JSON. */
    private static final int LINE_SEPARATOR = 0x2028;

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
                oldArtifact, newArtifact);
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
                new AnalysisConcurrency(2, 1, 2, 1),
                Map.of("module-analysis", 10L),
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.K_OBJ));

        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config"), List.of(), null);
        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final String index = Files.readString(output);
        assertThat(index).contains("How to read this report")
                .contains("Analysis scope and limitations")
                .contains("Terminology")
                .contains("k-Object (experimental)")
                .contains("<th>Result refinement algorithms</th><td>none"
                        + "</td>")
                .contains("<th>SSA equivalence</th><td>"
                        + "disabled (experimental)</td>")
                .contains("<th>SSA equivalence workers</th><td>"
                        + "0 (disabled)</td>")
                .contains("<th>SSA equivalent / different / unknown</th>"
                        + "<td>not run</td>")
                .contains("Maven Dependency Plugin")
                .contains("embedded 3.6.1")
                .contains("<th>JAR comparisons in parallel</th><td>1 "
                        + "(configured 2)</td>")
                .contains("<th>Impact queries in parallel</th><td>2 "
                        + "(configured 2)</td>")
                .contains("<th>Code comparisons in parallel</th><td>1 "
                        + "(configured 2)</td>")
                .doesNotContain("Modules analyzed in parallel")
                .contains("example:app:jar:1")
                .contains("example:new-module:jar:1")
                .contains("Skipped")
                .doesNotContain("vanilla-0-1-cfa");
        assertThat(owned.resolve("stale.html")).doesNotExist();
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            final Path moduleIndex = values.stream()
                    .filter(path -> !path.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow();
            final Path impact = values.stream()
                    .filter(path -> path.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow();
            assertThat(Files.readString(moduleIndex))
                    .contains("Stage elapsed time")
                    .contains("embedded 3.6.1")
                    .contains("Affected Paths")
                    .contains("id=\"changed-member-table\"")
                    .doesNotContain("Dependency Changes")
                    .contains("Technical details")
                    .contains("aria-label=\"Table of contents\"")
                    .contains("complete &lt;unsafe&gt;")
                    .contains("fixture comparison failure")
                    .doesNotContain("Module Diagnostics", "Diagnostics")
                    .doesNotContain("<unsafe>")
                    .doesNotContain("substring event");
            assertThat(Files.readString(impact))
                    .contains("No affected path matched the current filters.")
                    .contains("id=\"path-type\"")
                    .contains("<option value=\"20\" selected>20</option>")
                    .contains("<option value=\"100\">100</option>")
                    .contains("type=\"application/json\"")
                    .doesNotContain("Diagnostics")
                    .doesNotContain("-changes.html");
        }
    }

    @Test
    void keepsDenseChangedMemberTableBrowserResidentAndPageRendered()
            throws Exception {
        final Path output = temporary.resolve("dense.html");
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final List<BoundChangePoint> changes = new ArrayList<>();
        for (int index = 0; index < DENSE_MEMBER_COUNT; index++) {
            changes.add(new BoundChangePoint(upgrade, new ChangePoint(
                    newArtifact, ChangePointKind.METHOD_REMOVED,
                    "example/library/Api", "method" + index,
                    "()V", null, null)));
        }
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH,
                temporary.resolve("dense-classes"), List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(changes, List.of()));
        final ModuleAnalysisResult module = new ModuleAnalysisResult.Builder(
                unit).status(ModuleAnalysisStatus.SUCCESS,
                        ModuleAnalysisReason.NONE, "complete").build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(module), new AnalysisConcurrency(1, 1, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-dense"), List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("dense-modules");
        final String moduleIndex;
        try (Stream<Path> pages = Files.list(owned)) {
            moduleIndex = Files.readString(pages.filter(path ->
                    !path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(occurrences(moduleIndex,
                "\"changePointKind\":\"METHOD_REMOVED\""))
                .isEqualTo(DENSE_MEMBER_COUNT);
        assertThat(moduleIndex)
                .contains("<tbody id=\"member-rows\"></tbody>")
                .contains("filtered.slice(start, end)")
                .contains("rowsNode.replaceChildren(fragment)")
                .contains("const state = {query: \"\", kind: \"all\", "
                        + "pageSize: 20, page: 1}");
    }

    @Test
    void normalizesFinalAndStructuralRowsWithoutFilteredDetails()
            throws Exception {
        final Path output = temporary.resolve("filtered.html");
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final BoundChangePoint affected = new BoundChangePoint(upgrade,
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_BODY_CHANGED,
                        "example/library/Api", "changed", "()I",
                        "old", "new"));
        final BoundChangePoint hidden = new BoundChangePoint(upgrade,
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_REMOVED,
                        "example/library/Api", "hidden", "()V",
                        null, null));
        final QueryNode root = new ReportQueryNode(new MethodId(
                "example/app/Controller", "handle", "()V", "app",
                "/secret/work/classes"), CodeOrigin.PROJECT);
        final QueryNode secondRoot = new ReportQueryNode(new MethodId(
                "example/app/Controller", "search", "()V", "app",
                "/secret/work/classes"), CodeOrigin.PROJECT);
        final String unsafeDetail = "fixture </script><script>alert(1)"
                + "</script>&" + Character.toString(LINE_SEPARATOR);
        final ImpactPath candidate = new ImpactPath(List.of(root),
                new ChangePointTerminal(affected, referenceEvidence(
                        EvidenceMechanism.METHOD_DECLARATION, unsafeDetail)),
                ImpactClassification.TRANSITIVE,
                ImpactPathRootKind.METHOD);
        final ImpactPath secondCandidate = new ImpactPath(List.of(secondRoot),
                new ChangePointTerminal(affected, referenceEvidence(
                        EvidenceMechanism.METHOD_DECLARATION, "fixture two")),
                ImpactClassification.DIRECT, ImpactPathRootKind.METHOD);
        final StructuralReferencePath structural =
                new StructuralReferencePath(affected,
                        new StructuralReference("example/app/Config",
                                CodeOrigin.PROJECT,
                                StructuralReferenceKind.ANNOTATION, "",
                                "example/library/Api", "annotation fixture"),
                        List.of(root), ImpactClassification.DIRECT);
        final CodeComparisonEvidence code = new CodeComparisonEvidence(
                CodeComparisonStatus.AVAILABLE,
                List.of(new UnifiedDiffHunk(1, 1, 1, 1,
                        List.of("-return 1;", "+return 2;"))), "");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, temporary.resolve("classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(affected, hidden), List.of()));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .candidatePaths(List.of(candidate, secondCandidate))
                        .finalPaths(List.of())
                        .structuralPaths(List.of(structural))
                        .equivalenceResults(Map.of(affected,
                                new MethodEquivalenceResult(
                                        MethodEquivalenceStatus
                                                .PROVEN_EQUIVALENT,
                                        "fixture")))
                        .dispositions(Map.of(
                                affected,
                                ChangePointDisposition.FILTERED_EQUIVALENT,
                                hidden,
                                ChangePointDisposition.NO_PROJECT_PATH))
                        .codeComparisons(Map.of(affected, code)).build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(module), new AnalysisConcurrency(2, 1, 1, 1),
                Map.of(), new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA,
                        CallGraphAlgorithm.defaultKObjDepth(),
                        WalaReflectionOptions.defaultOptions(),
                        DependencyAnalysisScopeMode.defaultMode(),
                        JdkModelSelection.NONE,
                        ResultRefinementSelection.of(
                                ResultRefinementAlgorithm.SSA_EQUIVALENCE)));
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-filtered"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("filtered-modules");
        final String impact;
        final String moduleIndex;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            impact = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
            moduleIndex = Files.readString(values.stream().filter(path ->
                    !path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("<option value=\"final\" selected>Final</option>")
                .contains("<option value=\"structural\">Structural")
                .contains("<option value=\"all\">All</option>")
                .contains("example.app.Controller#handle")
                .contains("\"dependencyUpgrades\"")
                .contains("\"changedMembers\"")
                .contains("\"pathSteps\"")
                .contains("\"codeDiffs\"")
                .contains("\"pathMemberRows\"")
                .contains("\"changePointKind\":\"METHOD_BODY_CHANGED\"")
                .contains("diff-line diff-add")
                .contains("<tbody id=\"path-rows\"></tbody>")
                .doesNotContain("Equivalent filtered",
                        "example.app.Controller#search")
                .doesNotContain("\"evidence\":", "oldDescriptor",
                        "newDescriptor", "oldHash", "newHash",
                        "ssaReason", "observations", "asmFallback")
                .doesNotContain("/secret/work/classes")
                .doesNotContain("fixture </script><script>alert(1)")
                .doesNotContain(Character.toString(LINE_SEPARATOR))
                .doesNotContain("#hidden", "-changes.html");
        assertThat(occurrences(impact, "\"unifiedDiff\""))
                .isEqualTo(1);
        assertThat(occurrences(impact, "\"changedMemberId\":0"))
                .isEqualTo(1);
        assertThat(moduleIndex)
                .contains("id=\"changed-member-table\"")
                .contains("\"candidate\":2")
                .contains("\"filtered\":2")
                .contains("\"final\":0")
                .contains("\"structural\":1")
                .contains("\"name\":\"hidden\"");
        assertThat(output).content()
                .contains("<th>Result refinement algorithms</th><td>"
                        + "ssa-equivalence (experimental)</td>")
                .contains("<th>SSA equivalence</th><td>"
                        + "enabled (experimental)</td>")
                .contains("<th>SSA equivalence workers</th><td>"
                        + "1 (experimental)</td>")
                .contains("<th>SSA equivalent / different / unknown</th>"
                        + "<td>1 / 0 / 0</td>");
        assertThat(impact)
                .contains("example:library:jar:1", "example:library:jar:2")
                .contains("-return 1;")
                .contains("+return 2;");
    }

    @Test
    void rendersScopeValidationWarningAsInconclusiveLimitation()
            throws Exception {
        final Path output = temporary.resolve("scope-warning.html");
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord artifact = new ArtifactCoord(
                "example", "legacy", "jar", "1");
        final ScopeValidationWarning warning = new ScopeValidationWarning(
                artifact, 1, 1, List.of(
                        "legacy/AppletConfig.class -> java/applet/Applet"));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH,
                temporary.resolve("scope-warning-classes"),
                List.of(), List.of(artifact), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .status(ModuleAnalysisStatus.INCONCLUSIVE,
                                ModuleAnalysisReason
                                        .INCONCLUSIVE_SCOPE_VALIDATION,
                                "Analysis completed with coverage "
                                        + "limitations")
                        .limitations(List.of(warning.summary()))
                        .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.INCONCLUSIVE,
                List.of(), List.of(module),
                new AnalysisConcurrency(2, 1, 1, 1), Map.of(),
                EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-scope-warning"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()),
                maven(), plugin, java(), output);

        assertThat(output).content()
                .contains("Completed with coverage limitations")
                .contains("Analysis completed with coverage limitations");
        final Path owned = temporary.resolve("scope-warning-modules");
        final String index;
        try (Stream<Path> pages = Files.list(owned)) {
            index = Files.readString(pages
                    .filter(path -> !path.getFileName().toString()
                            .contains("-impact"))
                    .filter(path -> !path.getFileName().toString()
                            .contains("-changes"))
                    .findFirst().orElseThrow());
        }
        final String warningPrefix =
                "External dependency references excluded JDK classes";
        final int limitationHeading = index.indexOf(
                "Coverage limitations</h2>");
        assertThat(index)
                .contains("<strong>Completed with coverage limitations:"
                        + "</strong>")
                .contains("INCONCLUSIVE_SCOPE_VALIDATION")
                .doesNotContain("Diagnostics", "scope-validation",
                        "REVERSE_BFS")
                .doesNotContain("legacy.jar")
                .doesNotContain("<strong>Failed:</strong>");
        assertThat(index.indexOf(warningPrefix, limitationHeading))
                .isGreaterThan(limitationHeading);
    }

    @Test
    void reportsDuplicateWinnerAndShadowedChangeWithoutImpactPath()
            throws Exception {
        final Path output = temporary.resolve("duplicate.html");
        final Path winner = temporary.resolve("winner-<unsafe>");
        final Path shadowed = temporary.resolve("secret-shadowed.jar");
        writeClass(winner, "sample/Duplicate.class", new byte[]{1});
        writeJarClass(shadowed, "sample/Duplicate.class", new byte[]{2});
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        try (JarFile jar = new JarFile(shadowed.toFile(), false)) {
            ownership.addJar(newArtifact, jar, CodeOrigin.DEPENDENCY);
        }
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final BoundChangePoint bound = new BoundChangePoint(upgrade,
                new ChangePoint(newArtifact, ChangePointKind.METHOD_REMOVED,
                        "sample/Duplicate", "removed", "()V", null, null));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, winner, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(bound), List.of()));
        final ModuleAnalysisResult module = new ModuleAnalysisResult
                .Builder(unit)
                .status(ModuleAnalysisStatus.SUCCESS,
                        ModuleAnalysisReason.NONE, "complete")
                .dispositions(Map.of(bound,
                        ChangePointDisposition.SHADOWED_BY_DUPLICATE))
                .duplicateClassResolutions(
                        ownership.duplicateClassResolutions())
                .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(module), new AnalysisConcurrency(1, 1, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-duplicate"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("Conflicting duplicate classes</th><td>1")
                .contains("Shadowed dependency changes</th><td>1")
                .contains("Completed");
        final Path owned = temporary.resolve("duplicate-modules");
        final String index;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            assertThat(values.stream().noneMatch(path -> path.getFileName()
                    .toString().contains("-changes"))).isTrue();
            index = Files.readString(values.stream()
                    .filter(path -> !path.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(index)
                .contains("Duplicate class resolution")
                .contains("sample.Duplicate")
                .contains("Current module target/classes precedence")
                .contains("winner-&lt;unsafe&gt;")
                .contains("example:library:jar:2")
                .contains("No module-specific limitation was recorded.")
                .doesNotContain("winner-<unsafe>")
                .doesNotContain(shadowed.toString());
    }

    @Test
    void rendersDefaultChaAlgorithmAndTerminology() {
        final Path output = temporary.resolve("cha.html");
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(), new AnalysisConcurrency(1, 0, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-cha"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("<th>Algorithm</th><td>cha</td>")
                .contains("<th>WALA ReflectionOptions</th><td>"
                        + "not applied by cha (configured: "
                        + "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD)")
                .contains("<th>JDK method model</th><td>none</td>")
                .doesNotContain("availableTarget", "unavailableTarget",
                        "hitTarget")
                .contains("CHA")
                .contains("does not build points-to facts");
    }

    @Test
    void rendersKObjAlgorithmDepthAndTerminology() {
        final Path output = temporary.resolve("k-obj.html");
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(), new AnalysisConcurrency(1, 0, 0, 0),
                Map.of(), new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.K_OBJ, 2,
                        WalaReflectionOptions.defaultOptions(),
                        DependencyAnalysisScopeMode.defaultMode(),
                        JdkModelSelection.defaultSelection()));
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-one-object"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("<th>Algorithm</th><td>k-obj (experimental)</td>")
                .contains("<th>k-object depth</th><td>2</td>")
                .contains("k-Object")
                .contains("configured number of receiver allocation sites")
                .contains("without smushing")
                .contains("substantially more time and memory");
    }

    @Test
    void rendersAccessRemainsValidWithoutAffectedCallChain()
            throws Exception {
        final Path output = temporary.resolve("access-valid.html");
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PROTECTED);
        final BoundChangePoint access = new BoundChangePoint(upgrade,
                ChangePoint.accessNarrowed(newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "example/library/Api", "call", "()V",
                        transition));
        final AccessReferenceEvidence observation =
                new AccessReferenceEvidence(
                        transition, AccessDecision.ACCESSIBLE,
                        AccessDecisionReason.SAME_RUNTIME_PACKAGE,
                        "example/library/Peer#run()V",
                        "example/library/Api", "example/library/Api",
                        "NOT_APPLICABLE", "example/library/Api#call()V");
        final CodeComparisonEvidence comparison =
                new CodeComparisonEvidence(
                        CodeComparisonStatus.AVAILABLE,
                        List.of(new UnifiedDiffHunk(1, 1, 1, 1,
                                List.of("-public void call()",
                                        "+protected void call()"))),
                        "");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH,
                temporary.resolve("access-classes"), List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(access), List.of()));
        final ModuleAnalysisResult module = new ModuleAnalysisResult
                .Builder(unit)
                .status(ModuleAnalysisStatus.SUCCESS,
                        ModuleAnalysisReason.NONE, "complete")
                .dispositions(Map.of(access,
                        ChangePointDisposition.ACCESS_REMAINS_VALID))
                .observations(Map.of(access, List.of(observation)))
                .codeComparisons(Map.of(access, comparison))
                .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(module), new AnalysisConcurrency(1, 1, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-access"), List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("access-valid-modules");
        final String impact;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            impact = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("No affected path matched the current filters.")
                .contains("\"changedMembers\":[]")
                .contains("\"paths\":[]")
                .contains("\"pathMemberRows\":[]")
                .doesNotContain("example.library.Api#call")
                .doesNotContain("decision=ACCESSIBLE")
                .doesNotContain("-public void call()")
                .doesNotContain("+protected void call()", "-changes.html");
    }

    @Test
    void rendersPotentialAccessAsPotentialInAffectedCallChain()
            throws Exception {
        final Path output = temporary.resolve("access-potential.html");
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PROTECTED);
        final BoundChangePoint access = new BoundChangePoint(upgrade,
                ChangePoint.accessNarrowed(newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "example/library/Api", "call", "()V",
                        transition));
        final AccessReferenceEvidence evidence =
                new AccessReferenceEvidence(
                        transition,
                        AccessDecision.POTENTIALLY_INACCESSIBLE,
                        AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN,
                        "example/app/Controller#handle()V",
                        "example/library/Api", "example/library/Api",
                        "UNKNOWN", "example/library/Api#call()V");
        final QueryNode root = new ReportQueryNode(new MethodId(
                "example/app/Controller", "handle", "()V", "app",
                "app/classes"), CodeOrigin.PROJECT);
        final ImpactPath path = new ImpactPath(List.of(root),
                new ChangePointTerminal(access,
                        referenceEvidence(
                                EvidenceMechanism.DECLARED_INVOKE,
                                evidence.render())),
                ImpactClassification.TRANSITIVE,
                ImpactPathRootKind.METHOD);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH,
                temporary.resolve("potential-classes"), List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(access), List.of()));
        final ModuleAnalysisResult module = new ModuleAnalysisResult
                .Builder(unit)
                .status(ModuleAnalysisStatus.SUCCESS,
                        ModuleAnalysisReason.NONE, "complete")
                .candidatePaths(List.of(path))
                .finalPaths(List.of(path))
                .dispositions(Map.of(access,
                        ChangePointDisposition.IMPACT_REPORTED))
                .observations(Map.of(access, List.of(evidence)))
                .build();
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(module), new AnalysisConcurrency(1, 1, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-potential"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("access-potential-modules");
        final String impact;
        try (Stream<Path> pages = Files.list(owned)) {
            impact = Files.readString(pages.filter(pathValue ->
                    pathValue.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("\"changePointKind\":"
                        + "\"METHOD_ACCESS_NARROWED\"")
                .contains("example.app.Controller#handle")
                .doesNotContain("decision=POTENTIALLY_INACCESSIBLE",
                        "PROTECTED_RECEIVER_UNKNOWN", "report-fixture",
                        "bytecodePc", "\"evidence\":")
                .doesNotContain("IllegalAccessError");
    }

    /**
     * Minimal report-only query node.
     *
     * @param methodId method identity
     * @param origin code origin
     */
    private record ReportQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }

    private int occurrences(final String text, final String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private void writeClass(
            final Path root,
            final String relative,
            final byte[] content) throws Exception {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private void writeJarClass(
            final Path jar,
            final String relative,
            final byte[] content) throws Exception {
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(relative));
            output.write(content);
            output.closeEntry();
        }
    }

    private MavenRuntimeDescriptor maven() {
        return new MavenRuntimeDescriptor(
                MavenRuntimeSource.USER_CONFIGURED,
                Path.of("mvn"), MavenVersion.parse("3.9.9"),
                null, temporary);
    }

    private JavaRuntimeDescriptor java() {
        return new JavaRuntimeDescriptor(temporary, temporary,
                "1.8.0", TARGET_MAJOR, List.of(), List.of());
    }

    private ReferenceEvidence referenceEvidence(
            final EvidenceMechanism mechanism,
            final String detail) {
        return new ReferenceEvidence(Optional.empty(),
                new ReferenceTarget("example/library/Api", "changed",
                        "()V"), EvidenceKind.METHOD_REFERENCE, mechanism,
                new EvidenceLocation("report-fixture", 0), detail);
    }
}
