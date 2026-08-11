package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.impact.AnalysisMode;
import io.github.dependencyanalysis.impact.AnalysisConcurrency;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.AnalysisRunConfiguration;
import io.github.dependencyanalysis.impact.AnalysisStatus;
import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ScopeValidationWarning;
import io.github.dependencyanalysis.impact.ChangePointTerminal;
import io.github.dependencyanalysis.impact.AccessDecision;
import io.github.dependencyanalysis.impact.AccessDecisionReason;
import io.github.dependencyanalysis.impact.AccessReferenceEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonStatus;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.MethodEquivalenceResult;
import io.github.dependencyanalysis.impact.MethodEquivalenceStatus;
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
                new AnalysisConcurrency(2, 1, 4, 1),
                Map.of("module-analysis", 10L),
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA));

        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config"), List.of(), null);
        final DiagnosticEvent exact = new DiagnosticEvent.Builder()
                .stage("module-analysis").substage("module")
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
                .contains("optimized-0-1-cfa")
                .contains("Optimized 0-1-CFA")
                .contains("Maven Dependency Plugin")
                .contains("embedded 3.6.1")
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
                    .contains("fixture comparison failure")
                    .doesNotContain("<unsafe>")
                    .doesNotContain("substring event");
            assertThat(Files.readString(impact))
                    .contains("No affected call chain was found within the "
                            + "documented analysis scope.")
                    .contains("Structural reference chains");
            assertThat(Files.readString(changes))
                    .contains("No dependency member associated with a "
                            + "candidate or final impact path")
                    .doesNotContain("Method removed")
                    .doesNotContain("fixture comparison failure")
                    .contains("Module Index");
        }
    }

    @Test
    void showsOnlyPathAssociatedChangesAndFoldsFilteredCodeEvidence()
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
        final ImpactPath candidate = new ImpactPath(List.of(root), List.of(),
                new ChangePointTerminal(affected, EdgeKind.METHOD_CHANGE,
                        "fixture"), ImpactClassification.TRANSITIVE);
        final CodeComparisonEvidence code = new CodeComparisonEvidence(
                CodeComparisonStatus.AVAILABLE,
                List.of(new UnifiedDiffHunk(1, 1, 1, 1,
                        List.of("-return 1;", "+return 2;"))), "", "");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, temporary.resolve("classes"),
                List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(affected, hidden), List.of()));
        final ModuleAnalysisResult module =
                new ModuleAnalysisResult.Builder(unit)
                        .candidatePaths(List.of(candidate))
                        .finalPaths(List.of())
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
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-filtered"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("filtered-modules");
        final String impact;
        final String changes;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            impact = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
            changes = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-changes"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("View candidate chains filtered as equivalent")
                .contains("example.app.Controller#handle")
                .doesNotContain("/secret/work/classes");
        assertThat(changes)
                .contains("example:library 1 → 2")
                .contains("Equivalent (filtered)")
                .contains("View code changes")
                .contains("Decompiled Java representation")
                .contains("-return 1;")
                .doesNotContain("#hidden");
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
        final DiagnosticEvent diagnostic = new DiagnosticEvent.Builder()
                .stage("scope-validation").substage("module")
                .module(moduleId.stableKey())
                .artifact(artifact.toString())
                .level(DiagnosticLevel.WARN)
                .message(warning.summary()).build();
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-scope-warning"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run,
                List.of(diagnostic), new PreflightReport(List.of()),
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
        final int diagnosticHeading = index.indexOf(
                "Module Diagnostics</h2>");
        assertThat(index)
                .contains("<strong>Completed with coverage limitations:"
                        + "</strong>")
                .contains("INCONCLUSIVE_SCOPE_VALIDATION")
                .contains("[scope-validation][module][module=")
                .contains(";artifact=example:legacy:jar:1]")
                .doesNotContain("legacy.jar")
                .doesNotContain("<strong>Failed:</strong>");
        assertThat(index.indexOf(warningPrefix, limitationHeading))
                .isLessThan(diagnosticHeading);
        assertThat(index.indexOf(warningPrefix, diagnosticHeading))
                .isGreaterThan(diagnosticHeading);
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

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("Conflicting duplicate classes</th><td>1")
                .contains("Shadowed dependency changes</th><td>1")
                .contains("Completed");
        final Path owned = temporary.resolve("duplicate-modules");
        final String index;
        final String changes;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            index = Files.readString(values.stream()
                    .filter(path -> !path.getFileName().toString()
                            .contains("-impact"))
                    .filter(path -> !path.getFileName().toString()
                            .contains("-changes"))
                    .findFirst().orElseThrow());
            changes = Files.readString(values.stream()
                    .filter(path -> path.getFileName().toString()
                            .contains("-changes"))
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
        assertThat(changes)
                .contains("Method removed")
                .contains("Shadowed by duplicate")
                .contains("no Impact Path was generated")
                .contains("PROJECT — winner-&lt;unsafe&gt;")
                .contains("example:library:jar:2")
                .doesNotContain(shadowed.toString())
                .contains("SHADOWED_BY_DUPLICATE");
    }

    @Test
    void rendersDefaultRtaAlgorithmAndTerminology() {
        final Path output = temporary.resolve("rta.html");
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(), new AnalysisConcurrency(1, 0, 0, 0),
                Map.of(), EntrypointSelection.allProjectClasses());
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-rta"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("<th>Algorithm</th><td>rta</td>")
                .contains("<th>WALA ReflectionOptions</th><td>"
                        + "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD</td>")
                .contains("<th>JDK method model</th><td>jdk8</td>")
                .doesNotContain("availableTarget", "unavailableTarget",
                        "hitTarget")
                .contains("RTA")
                .contains("global set of instantiated compatible classes")
                .contains("does not track allocation-site or value "
                        + "points-to dataflow")
                .doesNotContain("ZeroCFA")
                .doesNotContain("Optimized 0-1-CFA");
    }

    @Test
    void rendersExplicitZeroCfaAlgorithmAndTerminology() {
        final Path output = temporary.resolve("zero-cfa.html");
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(), new AnalysisConcurrency(1, 0, 0, 0),
                Map.of(), new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.ZERO_CFA));
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-zero-cfa"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("<th>Algorithm</th><td>zero-cfa</td>")
                .contains("ZeroCFA")
                .contains("concrete class")
                .contains("constant-specific identity")
                .doesNotContain("RTA")
                .doesNotContain("Optimized 0-1-CFA");
    }

    @Test
    void rendersOneObjectOneCallSiteAlgorithmAndTerminology() {
        final Path output = temporary.resolve("one-object.html");
        final AnalysisRunResult run = new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS, List.of(),
                List.of(), new AnalysisConcurrency(1, 0, 0, 0),
                Map.of(), new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE));
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        temporary.resolve("config-one-object"),
                        List.of(), null);

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        assertThat(output).content()
                .contains("<th>Algorithm</th><td>"
                        + "1-object-1-call-site</td>")
                .contains("1-Object + 1-Call-Site")
                .contains("one receiver allocation site")
                .contains("one call site")
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
                        "", "");
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

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
                new PreflightReport(List.of()), maven(), plugin,
                java(), output);

        final Path owned = temporary.resolve("access-valid-modules");
        final String impact;
        final String changes;
        try (Stream<Path> pages = Files.list(owned)) {
            final List<Path> values = pages.toList();
            impact = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-impact"))
                    .findFirst().orElseThrow());
            changes = Files.readString(values.stream().filter(path ->
                    path.getFileName().toString().contains("-changes"))
                    .findFirst().orElseThrow());
        }
        assertThat(impact)
                .contains("No affected call chain was found")
                .doesNotContain("example.library.Api#call");
        assertThat(changes)
                .contains("Method access narrowed")
                .contains("Access remains valid")
                .contains("Old access</th><td>PUBLIC")
                .contains("New access</th><td>PROTECTED")
                .contains("decision=ACCESSIBLE")
                .contains("View code changes")
                .contains("-public void call()")
                .contains("+protected void call()");
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
        final ImpactPath path = new ImpactPath(List.of(root), List.of(),
                new ChangePointTerminal(access,
                        EdgeKind.DECLARED_INVOKE_REFERENCE, evidence),
                ImpactClassification.TRANSITIVE);
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

        new PerModuleHtmlReportGenerator().generate(run, List.of(),
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
                .contains("Potential access incompatibility")
                .contains("decision=POTENTIALLY_INACCESSIBLE")
                .contains("reason=PROTECTED_RECEIVER_UNKNOWN")
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
}
