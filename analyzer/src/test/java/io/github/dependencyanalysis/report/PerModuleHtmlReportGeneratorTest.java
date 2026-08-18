package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.DecompileComparisonStatus;
import io.github.dependencyanalysis.bytecode.DecompileComparisonSummary;
import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.bytecode.MethodBodySuppressionReason;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.bytecode.SsaComparisonStatus;
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
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

    /** Example baseline class major version. */
    private static final int BASELINE_CLASS_MAJOR = 49;

    /** Example target class major version. */
    private static final int TARGET_CLASS_MAJOR = 50;

    /** Example SSA comparison elapsed milliseconds. */
    private static final long SSA_ELAPSED_MILLIS = 3L;

    /** Example decompilation comparison elapsed milliseconds. */
    private static final long DECOMPILE_ELAPSED_MILLIS = 5L;

    /** Example query elapsed metric. */
    private static final long QUERY_ELAPSED_MILLIS = 4L;

    /** Pages per analyzed Module. */
    private static final int MODULE_PAGE_COUNT = 2;

    /** Normalized Impact/Structural relations in the fixture. */
    private static final int PATH_MEMBER_RELATION_COUNT = 3;

    /** Dense changed member fixture size. */
    private static final int DENSE_MEMBER_COUNT = 2_501;

    /** Unicode line separator protected in embedded JSON. */
    private static final int LINE_SEPARATOR = 0x2028;

    /** Unicode paragraph separator protected in embedded JSON. */
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    /** Small shard limit used to force multiple deterministic shards. */
    private static final int TEST_SHARD_BYTES = 220;

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
        generator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final String index = Files.readString(output);
        assertThat(index).contains("How to read this report")
                .contains("Analysis scope and limitations")
                .contains("Terminology")
                .contains("k-Object (experimental)")
                .contains("<th>Method body equivalence order</th><td>"
                        + "Decompiled Java first; normalized SSA on miss</td>")
                .doesNotContain("Result refinement algorithms")
                .contains("<th>SSA matched / different / unknown / skipped"
                        + "</th><td>0 / 0 / 0 / 0</td>")
                .doesNotContain("SSA equivalence workers")
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
            final List<Path> values = pages.filter(Files::isRegularFile)
                    .toList();
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

        generator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("dense-modules");
        final String moduleIndex;
        try (Stream<Path> pages = Files.list(owned)) {
            moduleIndex = Files.readString(pages
                    .filter(Files::isRegularFile).filter(path ->
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
                .contains("const state = {query: \"\", includes: [], "
                        + "excludes: [], kind: \"all\"");
    }

    @Test
    void rendersImpactAndStructuralRowsWithoutPathLevelSsaDetails()
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
        final String unsafeDiff = "+String marker = \"</script>&"
                + Character.toString(LINE_SEPARATOR)
                + Character.toString(PARAGRAPH_SEPARATOR) + "\";";
        final ImpactPath impactPath = new ImpactPath(List.of(root),
                new ChangePointTerminal(affected, referenceEvidence(
                        EvidenceMechanism.METHOD_DECLARATION, unsafeDetail)),
                ImpactClassification.TRANSITIVE,
                ImpactPathRootKind.METHOD);
        final ImpactPath secondImpactPath = new ImpactPath(
                List.of(secondRoot),
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
                        List.of("-return 1;", "+return 2;",
                                unsafeDiff))), "");
        final SsaComparisonEvidence ssa = new SsaComparisonEvidence(
                new DependencyChange(ChangeType.VERSION_CHANGED,
                        oldArtifact, newArtifact, DependencyScope.COMPILE,
                        "app"), affected.getChangePoint(),
                BASELINE_CLASS_MAJOR, TARGET_CLASS_MAJOR,
                SsaComparisonStatus.MATCHED,
                "NORMALIZED_SSA_CFG_ISOMORPHIC", SSA_ELAPSED_MILLIS);
        final DecompileComparisonSummary decompiled =
                decompiledSummary(oldArtifact, newArtifact);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, temporary.resolve("classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of(affected, hidden),
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(ssa), List.of(decompiled)));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .impactPaths(List.of(impactPath, secondImpactPath))
                        .structuralPaths(List.of(structural))
                        .dispositions(Map.of(
                                affected,
                                ChangePointDisposition.IMPACT_REPORTED,
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
                        JdkModelSelection.NONE));
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-filtered"),
                        List.of(), null);

        final ShardedReport report = generateShardedReport(
                run, plugin, output);
        assertThat(report.impact())
                .contains("<option value=\"impact\" selected>Impact</option>")
                .contains("<option value=\"structural\">Structural")
                .contains("<option value=\"all\">All</option>")
                .contains("id=\"affected-path-manifest\"")
                .contains("\"schemaVersion\":4")
                .contains("\"sources\"")
                .contains("source-index-00000.js")
                .contains("\"shards\"")
                .contains("index-00001.js")
                .contains("paths-00001.js")
                .contains("Searching affected methods:")
                .contains("searchGeneration")
                .contains("Retry")
                .contains("diff-line diff-add")
                .contains("<tbody id=\"path-rows\"></tbody>")
                .doesNotContain("Equivalent filtered")
                .doesNotContain("\"evidence\":", "oldDescriptor",
                        "newDescriptor", "oldHash", "newHash",
                        "ssaReason", "observations", "asmFallback")
                .doesNotContain("/secret/work/classes")
                .doesNotContain("fixture </script><script>alert(1)")
                .doesNotContain(Character.toString(LINE_SEPARATOR))
                .doesNotContain("fetch(")
                .doesNotContain("#hidden", "-changes.html");
        assertAffectedPathInteraction(report.impact());
        assertAffectedPathShards(report.shards());
        assertShardLimits(report.directory());
        assertShardDiagnostics(report.diagnostics());
        assertNormalizedRelationCounts(report.shards());
        assertChangedMemberMetrics(report.moduleIndex());
        assertChaPruningDisclosure(report.moduleIndex());
        final String overall = Files.readString(output);
        assertThat(overall)
                .contains("<th>Method body equivalence order</th><td>"
                        + "Decompiled Java first; normalized SSA on miss</td>")
                .doesNotContain("Result refinement algorithms")
                .doesNotContain("SSA equivalence workers");
        assertSemanticComparisonEvidence(overall);
        assertThat(report.shards())
                .contains("example:library:jar:1", "example:library:jar:2")
                .contains("-return 1;")
                .contains("+return 2;")
                .contains("\\u003c/script\\u003e", "\\u0026",
                        "\\u2028", "\\u2029");
        assertRepeatablePublication(report, run, plugin, output);
    }

    private DecompileComparisonSummary decompiledSummary(
            final ArtifactCoord oldArtifact,
            final ArtifactCoord newArtifact) {
        return new DecompileComparisonSummary(oldArtifact, newArtifact,
                "example/library/Api", "filtered", "()I",
                "old-filtered", "new-filtered", BASELINE_CLASS_MAJOR,
                TARGET_CLASS_MAJOR,
                DecompileComparisonStatus.DIFFERENT,
                "DECOMPILED_JAVA_TEXT_DIFFERENT",
                DECOMPILE_ELAPSED_MILLIS,
                java.util.Set.of(MethodBodySuppressionReason.SSA_MATCHED));
    }

    private void assertSemanticComparisonEvidence(final String overall) {
        assertThat(overall)
                .contains("<th>SSA matched / different / unknown / skipped"
                        + "</th><td>1 / 0 / 0 / 0</td>")
                .contains("<th>Method body equivalence order</th>"
                        + "<td>Decompiled Java first; normalized SSA on "
                        + "miss</td>")
                .contains("<th>Java identical / different / unknown</th>"
                        + "<td>0 / 1 / 0</td>")
                .contains("SSA ChangePoint collection evidence")
                .contains("Decompiled Java ChangePoint collection evidence")
                .contains("49 → 50")
                .contains("NORMALIZED_SSA_CFG_ISOMORPHIC")
                .contains("DECOMPILED_JAVA_TEXT_DIFFERENT")
                .contains("SSA_MATCHED")
                .contains("3 ms")
                .contains("5 ms");
    }

    private void assertChaPruningDisclosure(final String moduleIndex) {
        assertThat(moduleIndex)
                .contains("CHA does not expand JDK-declared virtual or "
                        + "interface dispatch")
                .contains("callback, SPI, lambda, collection implementation")
                .contains("This predefined scope does not change the "
                        + "Module status.")
                .contains("<th>cha-local-receiver-inference</th>")
                .doesNotContain("cha-adjacent-receiver-inference")
                .contains("CHA Impact Path pruning uses caller-local "
                        + "receiver facts only")
                .contains("<th>JDK-declared dispatch targets pruned</th>");
    }

    private ShardedReport generateShardedReport(
            final AnalysisRunResult run,
            final MavenDependencyPluginRuntime plugin,
            final Path output) throws Exception {
        final ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(new PrintStream(
                logOutput, true, StandardCharsets.UTF_8),
                LogVerbosity.TRACE);
        final PerModuleHtmlReportGenerator reportGenerator =
                new PerModuleHtmlReportGenerator(log, TEST_SHARD_BYTES);
        reportGenerator.generate(run, new PreflightReport(List.of()),
                maven(), plugin, java(), output);
        final Path directory = temporary.resolve("filtered-modules");
        final List<Path> pages;
        try (Stream<Path> files = Files.list(directory)) {
            pages = files.filter(Files::isRegularFile).toList();
        }
        assertThat(pages).hasSize(MODULE_PAGE_COUNT);
        final String impact = Files.readString(pages.stream().filter(path ->
                path.getFileName().toString().contains("-impact"))
                .findFirst().orElseThrow());
        final String moduleIndex = Files.readString(pages.stream()
                .filter(path -> !path.getFileName().toString()
                        .contains("-impact"))
                .findFirst().orElseThrow());
        return new ShardedReport(directory, impact, moduleIndex,
                shardContent(directory), logOutput.toString(
                StandardCharsets.UTF_8), reportGenerator);
    }

    private void assertRepeatablePublication(
            final ShardedReport report,
            final AnalysisRunResult run,
            final MavenDependencyPluginRuntime plugin,
            final Path output) throws Exception {
        final Map<String, String> original = shardFiles(report.directory());
        final Path orphan = report.directory().resolve(
                "orphan-impact-data/orphan.js");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan");
        report.generator().generate(run, new PreflightReport(List.of()),
                maven(), plugin, java(), output);
        assertThat(shardFiles(report.directory())).isEqualTo(original);
        assertThat(orphan).doesNotExist();
    }

    private void assertAffectedPathShards(final String shards) {
        assertThat(shards)
                .contains("example.app.Controller#handle")
                .contains("example.app.Controller#search")
                .contains("\"pathId\":0", "\"rowId\":0")
                .contains("\"type\":\"impact\"")
                .contains("\"type\":\"structural\"")
                .contains("\"project\":true")
                .contains("\"scope\":\"compile\"")
                .contains("\"source\":\"example:library\"")
                .contains("\"rowStart\":0", "\"rowCount\":")
                .contains("\"codeDiffStatus\":\"AVAILABLE\"")
                .contains("\"changePointKind\":"
                        + "\"METHOD_BODY_CHANGED\"")
                .doesNotContain("fixture </script><script>alert(1)")
                .doesNotContain(Character.toString(LINE_SEPARATOR))
                .doesNotContain(Character.toString(PARAGRAPH_SEPARATOR));
    }

    private void assertAffectedPathInteraction(final String impact) {
        assertThat(impact)
                .contains("id=\"path-search-form\"")
                .contains("id=\"path-search-submit\"")
                .contains("id=\"path-dependency-include\"")
                .contains("id=\"path-dependency-exclude\"")
                .contains("searchForm.addEventListener(\"submit\"")
                .contains("function pathSegments(path, member, methods)")
                .contains("function appendPathSequenceCell(")
                .contains("document.createTextNode(\" → \")")
                .contains("document.createElement(\"br\")")
                .contains("path.type === \"impact\"")
                .contains("(index + 1) % 2 === 0")
                .contains("white-space:normal;overflow-wrap:anywhere")
                .doesNotContain(".path-sequence{white-space:nowrap")
                .doesNotContain("searchInput.addEventListener(\"input\"")
                .doesNotContain("max-width:1440px");
    }

    private void assertShardLimits(final Path reportDirectory)
            throws Exception {
        boolean oversized = false;
        try (Stream<Path> files = Files.walk(reportDirectory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".js"))
                    .toList()) {
                final long bytes = Files.size(file);
                if (bytes > TEST_SHARD_BYTES) {
                    oversized = true;
                    assertThat(shardRecordCount(file)).as(file.toString())
                            .isEqualTo(1);
                }
            }
        }
        assertThat(oversized).isTrue();
    }

    private int shardRecordCount(final Path file) throws Exception {
        final String script = Files.readString(file);
        final int start = script.indexOf('(') + 1;
        final int end = script.lastIndexOf(");");
        try (JsonParser parser = ScriptSafeJson.factory().createParser(
                script.substring(start, end))) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME
                        && "records".equals(parser.currentName())) {
                    assertThat(parser.nextToken()).isEqualTo(
                            JsonToken.START_ARRAY);
                    int count = 0;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        count++;
                        parser.skipChildren();
                    }
                    return count;
                }
            }
        }
        throw new IllegalArgumentException("Missing records: " + file);
    }

    private void assertShardDiagnostics(final String diagnostics) {
        assertThat(diagnostics)
                .contains("[report][affected-path-shard]")
                .contains("shardCounts=index=")
                .contains(",rows=", ",paths=", ",methods=",
                        ",members=", ",dependencies=", ",diffs=")
                .contains("oversizedShards=")
                .contains("written; kind=index; progress=1/")
                .contains("; records=")
                .contains("; bytes=");
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

        generator().generate(run,
                new PreflightReport(List.of()),
                maven(), plugin, java(), output);

        assertThat(output).content()
                .contains("Completed with coverage limitations")
                .contains("Analysis completed with coverage limitations");
        final Path owned = temporary.resolve("scope-warning-modules");
        final String index;
        try (Stream<Path> pages = Files.list(owned)) {
            index = Files.readString(pages
                    .filter(Files::isRegularFile)
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

        generator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("Conflicting duplicate classes</th><td>1")
                .contains("Shadowed dependency changes</th><td>1")
                .contains("Completed");
        final Path owned = temporary.resolve("duplicate-modules");
        final String index;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.filter(Files::isRegularFile)
                    .toList();
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
                .contains("CHA does not expand JDK-declared virtual or "
                        + "interface dispatch")
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

        generator().generate(run,
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

        generator().generate(run,
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

        generator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("access-valid-modules");
        final String impact;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.filter(Files::isRegularFile)
                    .toList();
            assertThat(values).hasSize(MODULE_PAGE_COUNT);
            impact = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("No affected path matched the current filters.")
                .contains("\"schemaVersion\":4")
                .contains("\"count\":0")
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
                .impactPaths(List.of(path))
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

        generator().generate(run,
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("access-potential-modules");
        final String impact;
        try (Stream<Path> pages = Files.list(owned)) {
            impact = Files.readString(pages
                    .filter(Files::isRegularFile).filter(pathValue ->
                    pathValue.getFileName().toString()
                            .contains("-impact"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("id=\"affected-path-manifest\"")
                .doesNotContain("decision=POTENTIALLY_INACCESSIBLE",
                        "PROTECTED_RECEIVER_UNKNOWN", "report-fixture",
                        "bytecodePc", "\"evidence\"")
                .doesNotContain("IllegalAccessError");
        assertThat(shardContent(owned))
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

    /**
     * Generated Schema 4 fixture and its captured diagnostics.
     *
     * @param directory command-owned Module directory
     * @param impact Affected Paths HTML
     * @param moduleIndex Module Index HTML
     * @param shards concatenated shard payloads
     * @param diagnostics captured report diagnostics
     * @param generator configured generator used for repeat publication
     */
    private record ShardedReport(
            Path directory,
            String impact,
            String moduleIndex,
            String shards,
            String diagnostics,
            PerModuleHtmlReportGenerator generator) {
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

    private String shardContent(final Path reportDirectory)
            throws Exception {
        final StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.walk(reportDirectory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".js"))
                    .sorted().toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private Map<String, String> shardFiles(final Path reportDirectory)
            throws Exception {
        final Map<String, String> result = new java.util.TreeMap<>();
        try (Stream<Path> files = Files.walk(reportDirectory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".js"))
                    .toList()) {
                result.put(reportDirectory.relativize(file).toString(),
                        Files.readString(file));
            }
        }
        return result;
    }

    private void assertNormalizedRelationCounts(final String impact) {
        assertThat(occurrences(impact, "\"unifiedDiff\""))
                .isEqualTo(1);
        assertThat(occurrences(impact, "\"changedMemberId\":0"))
                .isEqualTo(PATH_MEMBER_RELATION_COUNT);
    }

    private void assertChangedMemberMetrics(final String moduleIndex) {
        assertThat(moduleIndex)
                .contains("id=\"changed-member-table\"")
                .contains("id=\"member-dependency-include\"")
                .contains("id=\"member-dependency-exclude\"")
                .contains("\"source\":\"example:library\"")
                .contains("\"impact\":2")
                .contains("\"structural\":1")
                .contains("\"name\":\"hidden\"")
                .contains("\"memberMetrics\":[{\"memberId\":0,"
                        + "\"impact\":2,\"structural\":1},{\"memberId\":1,"
                        + "\"impact\":0,\"structural\":0}]");
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

    private PerModuleHtmlReportGenerator generator() {
        return new PerModuleHtmlReportGenerator(new DiagnosticLog());
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
