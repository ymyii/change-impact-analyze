package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorScopeMode;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Offline tree report rendering tests. */
class TreeReportRendererTest {

    /** Move number used to inject second-target commit failure. */
    private static final int REPORT_COMMIT_MOVE = 4;

    /** Move number used to inject incremental index checkpoint failure. */
    private static final int INDEX_CHECKPOINT_MOVE = 5;

    /** Reactor count used to verify a fresh run checkpoint. */
    private static final int FRESH_RUN_REACTORS = 3;

    /** Temporary output parent. */
    @TempDir
    private Path temporary;

    @Test
    void rendersAllReactorScopeModesExactly() throws Exception {
        for (ReactorScopeMode mode : ReactorScopeMode.values()) {
            final Path pom = Path.of(mode.name().toLowerCase(), "pom.xml");
            final ReactorDescriptor descriptor = new ReactorDescriptor(
                    pom, "g:" + mode.name().toLowerCase() + ":1",
                    List.of(pom), List.of(pom), mode, List.of());
            final ReactorTreeResult reactor = new ReactorTreeResult(
                    descriptor, List.of(module("g:app:1", List.of(), "")),
                    ReactorStatus.SUCCESS, "");
            final Path output = temporary.resolve(mode.name());

            new TreeReportRenderer().render(
                    result(List.of(reactor)), output);

            assertThat(onlyReactorPage(output)).content()
                    .contains("<th>Analysis mode</th>")
                    .contains("<td>" + mode + "</td>");
        }
    }

    @Test
    void rendersStaticHierarchyWithoutTreeControlsAndEscapesContent()
            throws Exception {
        final DependencyOccurrence selected = occurrence(
                "g<script>", "leaf", true,
                List.of("g:root:jar:1", "g:parent:jar:1",
                        "g<script>:leaf:jar:1"));
        final DependencyOccurrence omitted = occurrence(
                "g", "other", false,
                List.of("g:root:jar:1", "g:parent:jar:1",
                        "g:other:jar:2"));
        final ModuleTreeResult module = module(
                "g:root:1", List.of(selected, omitted), "");
        final Path output = temporary.resolve("report");

        new TreeReportRenderer().render(
                result(List.of(reactor("pom.xml",
                        ReactorStatus.SUCCESS, "",
                        List.of(module)))), output);

        final String page = Files.readString(
                onlyReactorPage(output));
        assertThat(page)
                .contains("<h2>跨模块依赖分析</h2>")
                .contains("<h2>Module 分析</h2>")
                .contains("id=\"tree-report-manifest\"")
                .doesNotContain("<pre class=\"dependency-tree\">")
                .doesNotContain("<details")
                .doesNotContain("data-expand")
                .doesNotContain("data-collapse")
                .doesNotContain("data-dep-scope")
                .doesNotContain("data-dep-status")
                .doesNotContain("<script>g");
        assertThat(allShardContent(pageDataDirectory(
                onlyReactorPage(output))))
                .contains("g:root:jar:1\\n\\\\- g:parent:jar:1\\n")
                .contains("   +- g\\u003cscript\\u003e:leaf:jar:1:compile")
                .contains("   \\\\- g:other:jar:1:compile ")
                .contains("(omitted for conflict with 1)")
                .doesNotContain("<script>g");
        assertThat(output.resolve(
                "dependency-report/assets/report.js"))
                .content().contains("recordsForIds(\"dependency-trees\"")
                .contains("textContent")
                .doesNotContain("innerHTML");
    }

    @Test
    void preservesDuplicateSiblingOrderAndMavenConnectors()
            throws Exception {
        final List<String> path = List.of(
                "g:root:jar:1", "g:shared:jar:1:compile");
        final DependencyOccurrence selected = occurrence(
                "g", "shared", true, path);
        final DependencyOccurrence duplicate =
                new DependencyOccurrence(
                        new DependencyKey("g", "shared",
                                "jar", ""),
                        new OccurrenceVersions(
                                "1", "", "1", "1"),
                        new OccurrenceScopes("compile", ""),
                        null,
                        new OccurrenceSelection(false,
                                "duplicate"),
                        path, false);
        final Path output = temporary.resolve(
                "duplicate-order");

        new TreeReportRenderer().render(result(List.of(
                reactor("pom.xml", ReactorStatus.SUCCESS,
                        "", List.of(module("g:root:1",
                                List.of(selected, duplicate),
                                ""))))), output);

        assertThat(allShardContent(pageDataDirectory(
                onlyReactorPage(output))))
                .contains("g:root:jar:1\\n"
                        + "+- g:shared:jar:1:compile\\n"
                        + "\\\\- g:shared:jar:1:compile "
                        + "(omitted for duplicate)\\n");
    }

    @Test
    void rendersVerboseMavenAnnotationsInFixedOrder()
            throws Exception {
        final DependencyOccurrence occurrence =
                new DependencyOccurrence(
                        new DependencyKey("g", "leaf", "jar", ""),
                        new OccurrenceVersions("1", "1", "3", "3"),
                        new OccurrenceScopes("compile", "test"),
                        Boolean.TRUE,
                        new OccurrenceSelection(false, "conflict"),
                        List.of("g:root:jar:1", "g:leaf:jar:3"),
                        true);
        final Path output = temporary.resolve("verbose-tree");

        new TreeReportRenderer().render(result(List.of(reactor(
                "pom.xml", ReactorStatus.SUCCESS, "",
                List.of(module("g:root:1", List.of(occurrence), ""))))),
                output);

        final String tree = allShardContent(pageDataDirectory(
                onlyReactorPage(output)));
        assertThat(tree)
                .contains("g:root:jar:1\\n")
                .contains("\\\\- g:leaf:jar:3:compile "
                        + "(version managed from 1; "
                        + "scope managed from test; optional; "
                        + "omitted for conflict with 3; "
                        + "reactor module)");
    }

    @Test
    void repeatsReplacementWithStableFilenamesAndPreservesRoot()
            throws Exception {
        final Path output = temporary.resolve("report");
        Files.createDirectories(output.resolve(
                "dependency-report/old"));
        Files.writeString(output.resolve("keep.txt"),
                "keep");
        Files.writeString(output.resolve(
                "dependency-report/old/stale.html"),
                "stale");
        final ReactorTreeResult first = reactor(
                "a/pom.xml", ReactorStatus.SUCCESS,
                "", List.of(module("g:a:1",
                List.of(), "")));
        final ReactorTreeResult second = reactor(
                "b/pom.xml", ReactorStatus.SUCCESS,
                "", List.of(module("g:b:1",
                List.of(), "")));
        final TreeReportRenderer renderer =
                new TreeReportRenderer();

        renderer.render(result(List.of(first, second)),
                output);
        final List<String> initial = filenames(output);
        renderer.render(result(List.of(second, first)),
                output);

        assertThat(filenames(output))
                .containsExactlyElementsOf(initial);
        assertThat(output.resolve("keep.txt"))
                .hasContent("keep");
        assertThat(output.resolve(
                "dependency-report/old/stale.html"))
                .doesNotExist();
        assertThat(output.resolve("index.html"))
                .content().contains("dependency-report/reactors/");
    }

    @Test
    void distinguishesEmptyAndFailureDiagnostics()
            throws Exception {
        final ModuleTreeResult empty = module(
                "g:empty:1", List.of(), "");
        final ModuleTreeResult failed = module(
                "g:failed:1", List.of(),
                "Maven <failed>");
        final ReactorTreeResult reactor = reactor(
                "pom.xml", ReactorStatus.FAILED,
                "pom.xml: Maven <failed>",
                List.of(empty, failed));
        final Path output = temporary.resolve("report");

        new TreeReportRenderer().render(
                result(List.of(reactor)), output);

        final Path page = onlyReactorPage(output);
        assertThat(page).content()
                .contains("<h2>问题</h2>")
                .contains("REACTOR_ANALYSIS")
                .contains("MODULE_ANALYSIS")
                .contains("pom.xml: Maven &lt;failed&gt;")
                .contains("Maven &lt;failed&gt;")
                .doesNotContain("<pre class=\"dependency-tree\"></pre>")
                .doesNotContain("failure diagnostics")
                .doesNotContain("Maven <failed>");
        assertThat(allShardContent(pageDataDirectory(page)))
                .contains("\"failure\":\"Maven \\u003cfailed\\u003e\"")
                .contains("\"text\":\"\"");
    }

    @Test
    void summarizesModulesWithoutInclusionRoleOrReason()
            throws Exception {
        final ReactorTreeResult reactor = reactor(
                "pom.xml", ReactorStatus.SUCCESS, "",
                List.of(
                        module("g:requested:1", List.of(),
                                "", ModuleAnalysisRole.REQUESTED),
                        module("g:dependency:1", List.of(),
                                "", ModuleAnalysisRole.DEPENDENCY),
                        module("g:root:1", List.of(), "",
                                ModuleAnalysisRole
                                        .REACTOR_ROOT_SCOPE)));
        final Path output = temporary.resolve("roles");

        new TreeReportRenderer().render(
                result(List.of(reactor)), output);

        assertThat(onlyReactorPage(output))
                .content()
                .contains("<h2>Reactor metadata</h2>")
                .contains("<h2>Module metadata</h2>")
                .contains("<th>Dependencies</th>")
                .contains("<th>Internal conflicts</th>")
                .contains("<th>Multi-version dependencies</th>")
                .contains("g:requested:1")
                .contains("g:dependency:1")
                .contains("g:root:1")
                .contains("data-combobox=\"module-selector\"")
                .contains("role=\"combobox\"")
                .doesNotContain("role=\"tablist\"")
                .doesNotContain("REQUESTED</")
                .doesNotContain("DEPENDENCY</")
                .doesNotContain("REACTOR_ROOT_SCOPE")
                .doesNotContain("纳入原因")
                .doesNotContain("--path selected module")
                .doesNotContain("reactor dependency of a REQUESTED module");
    }

    @Test
    void usesFullCoordinatesInSearchableModuleCatalog()
            throws Exception {
        final ModuleTreeResult first = module(
                "g1:shared:1", List.of(), "");
        final ModuleTreeResult second = module(
                "g2:shared:1", List.of(), "");
        final ModuleTreeResult third = module(
                "g1:shared:2", List.of(), "");
        final Path firstOutput = temporary.resolve("tabs-first");
        new TreeReportRenderer().render(result(List.of(reactor(
                "pom.xml", ReactorStatus.SUCCESS, "",
                List.of(first, second, third)))), firstOutput);

        final String firstPage = Files.readString(
                onlyReactorPage(firstOutput));
        assertThat(firstPage)
                .contains("data-combobox=\"module-selector\"")
                .contains("\"coordinate\":\"g1:shared:1\"")
                .contains("\"coordinate\":\"g2:shared:1\"")
                .contains("\"coordinate\":\"g1:shared:2\"")
                .contains("aria-autocomplete=\"list\"")
                .contains("aria-required=\"true\"");
    }

    @Test
    void mergesConflictTypesWithOfflineTableInteractions()
            throws Exception {
        final DependencyOccurrence selectedOne = managedOccurrence(
                "g", "shared",
                new OccurrenceVersions("1", "1", "3", "3"), true,
                "compile", List.of("g:a:jar:1",
                        "g:shared:jar:3"));
        final DependencyOccurrence omittedTwo = occurrence(
                "g", "shared", "2", "3", false,
                "runtime", List.of("g:a:jar:1",
                        "g:shared:jar:2"));
        final DependencyOccurrence selectedTwo = occurrence(
                "g", "shared", "2", "2", true,
                "runtime", List.of("g:b:jar:1",
                        "g:shared:jar:2"));
        final ReactorTreeResult reactor = reactor(
                "pom.xml", ReactorStatus.SUCCESS, "",
                List.of(
                        module("g:a:1", List.of(
                                selectedOne, omittedTwo), ""),
                        module("g:b:1", List.of(
                                selectedTwo), "")));
        final Path output = temporary.resolve("conflicts");

        new TreeReportRenderer().render(
                result(List.of(reactor)), output);

        final String page = Files.readString(
                onlyReactorPage(output));
        assertThat(page)
                .containsOnlyOnce("<h2>跨模块依赖分析</h2>")
                .contains("class=\"dependency-filter-form\"")
                .contains("class=\"dependency-filter-primary\"")
                .contains("class=\"dependency-filter-secondary\"")
                .contains("data-combobox=\"dependency-filter\"")
                .contains("data-combobox=\"dependency-module-filter\"")
                .contains("id=\"dependency-filter-toggle\"")
                .contains("id=\"dependency-module-filter-toggle\"")
                .contains("id=\"module-selector-toggle\"")
                .contains("aria-label=\"展开 Dependency 候选\"")
                .contains("id=\"dependency-scope-filter\"")
                .contains("data-dependency-sort=\"module\"")
                .contains("data-dependency-sort=\"dependency\"")
                .contains("<option>10</option>")
                .contains("<option>50</option>")
                .contains("<option>100</option>")
                .contains("Dependency chain")
                .contains("Original version")
                .contains("Resolved version")
                .contains("\"resolvedVersionCount\":2")
                .doesNotContain("DEPENDENCY_PATH")
                .doesNotContain("g:a:jar:1 → g:shared:jar:3")
                .doesNotContain("<th>Status</th><th>Reason</th>")
                .doesNotContain("Module 内 version mediation")
                .doesNotContain("跨 Module resolved version 差异");
        assertThat(page)
                .contains("<td><code>g:a:1</code></td>"
                        + "<td><code>g-a-1/pom.xml</code></td>"
                        + "<td class=\"SUCCESS\">SUCCESS</td>"
                        + "<td>2</td><td>1</td><td>1</td>"
                        + "<td>0</td><td>0</td><td>0</td>")
                .contains("<td><code>g:b:1</code></td>"
                        + "<td><code>g-b-1/pom.xml</code></td>"
                        + "<td class=\"SUCCESS\">SUCCESS</td>"
                        + "<td>1</td><td>0</td><td>1</td>"
                        + "<td>0</td><td>0</td><td>0</td>");
        assertThat(allShardContent(pageDataDirectory(
                onlyReactorPage(output))))
                .contains("DEPENDENCY_PATH")
                .contains("DEPENDENCY_MANAGEMENT")
                .contains("g:a:jar:1 → g:shared:jar:3")
                .contains("g:b:jar:1 → g:shared:jar:2");
        assertThat(output.resolve(
                "dependency-report/assets/report.js"))
                .content()
                .contains("createCombobox")
                .contains("slice(0, 50)")
                .contains("intersectRanges")
                .contains("Loading dependency index")
                .contains("dependencyGeneration")
                .contains("recordsForIds(")
                .contains("localeCompare")
                .contains("Math.ceil")
                .contains("ArrowDown")
                .contains("ArrowUp")
                .contains("Home")
                .contains("End");
        assertThat(output.resolve("index.html"))
                .content()
                .contains("<h2>Metadata</h2>")
                .contains("<th>Field</th><th>Value</th>")
                .contains("<h2>Summary</h2>")
                .contains("<th>Status</th><th>Progress</th>")
                .contains("<th>Internal conflicts</th>")
                .contains("<th>Multi-version dependencies</th>")
                .contains("<td>3</td><td>1</td><td>1</td>");
    }

    @Test
    void keepsEmptyConflictTableAndHidesEmptyControlsAndIssues()
            throws Exception {
        final Path output = temporary.resolve("empty-conflicts");

        new TreeReportRenderer().render(result(List.of(
                reactor("pom.xml", ReactorStatus.SUCCESS, "",
                        List.of(module("g:a:1", List.of(), ""))))),
                output);

        final String page = Files.readString(
                onlyReactorPage(output));
        assertThat(page)
                .contains("<h2>跨模块依赖分析</h2>")
                .contains("<h2>Module 分析</h2>")
                .contains("<tbody id=\"dependency-rows\"></tbody>")
                .contains("\"dependencyRows\":0")
                .contains("\"dependencies\":[]")
                .doesNotContain("<h2>问题</h2>");
    }

    @Test
    void rollsBackBothOwnedTargetsWhenSecondCommitFails()
            throws Exception {
        final Path output = temporary.resolve("report");
        Files.createDirectories(output.resolve(
                "dependency-report"));
        Files.writeString(output.resolve("index.html"),
                "old-index");
        Files.writeString(output.resolve(
                "dependency-report/old-report.txt"),
                "old-report");
        final AtomicInteger moves = new AtomicInteger();
        final TreeReportRenderer renderer =
                new TreeReportRenderer((source, target) -> {
                    if (moves.incrementAndGet()
                            == REPORT_COMMIT_MOVE) {
                        throw new IOException(
                                "injected report commit failure");
                    }
                    Files.move(source, target);
                });

        assertThatThrownBy(() -> renderer.render(
                result(List.of(reactor("pom.xml",
                        ReactorStatus.SUCCESS, "",
                        List.of()))), output))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(
                        "injected report commit failure");

        assertThat(output.resolve("index.html"))
                .hasContent("old-index");
        assertThat(output.resolve(
                "dependency-report/old-report.txt"))
                .hasContent("old-report");
    }

    @Test
    void publishesRunningCheckpointBeforeCompletion()
            throws Exception {
        final Path output = temporary.resolve("incremental");
        final TreeRepositoryResult repository = result(
                List.of());
        final TreeReportSession session =
                new TreeReportRenderer().start(
                        TreeReportMetadata.from(repository),
                        2, output);

        assertThat(output.resolve("index.html"))
                .content().contains("RUNNING")
                .contains("<td>0/2</td>");

        session.publish(reactor("a/pom.xml",
                        ReactorStatus.SUCCESS, "",
                        List.of(module("g:a:1",
                                List.of(), ""))));

        assertThat(output.resolve("index.html"))
                .content().contains("RUNNING")
                .contains("<td>1/2</td>")
                .contains("a/pom.xml");
        assertThat(filenames(output)).hasSize(1);
        assertThat(session.getState())
                .isEqualTo(TreeReportState.RUNNING);
        assertThat(session.getCompletedReactors())
                .isEqualTo(1);
    }

    @Test
    void completesAndFailsWithTerminalCheckpoints()
            throws Exception {
        final TreeReportMetadata metadata =
                TreeReportMetadata.from(result(List.of()));
        final Path completeOutput = temporary.resolve(
                "complete");
        final TreeReportSession complete =
                new TreeReportRenderer().start(
                        metadata, 1, completeOutput);
        complete.publish(reactor("pom.xml",
                        ReactorStatus.SUCCESS, "",
                        List.of()));
        complete.complete();

        assertThat(completeOutput.resolve("index.html"))
                .content().contains("SUCCESS")
                .contains("<td>1/1</td>");
        assertThat(complete.getState())
                .isEqualTo(TreeReportState.SUCCESS);

        final Path failedOutput = temporary.resolve(
                "failed");
        final TreeReportSession failed =
                new TreeReportRenderer().start(
                        metadata, 2, failedOutput);
        failed.fail("pipeline <stopped>");

        assertThat(failedOutput.resolve("index.html"))
                .content().contains("FAILED")
                .contains("<td>0/2</td>")
                .contains("pipeline &lt;stopped&gt;")
                .doesNotContain("pipeline <stopped>");
        assertThat(failed.getState())
                .isEqualTo(TreeReportState.FAILED);
    }

    @Test
    void keepsPublishedPageWhenIndexCheckpointMoveFails()
            throws Exception {
        final Path output = temporary.resolve(
                "checkpoint-failure");
        final AtomicInteger moves = new AtomicInteger();
        final TreeReportRenderer renderer =
                new TreeReportRenderer((source, target) -> {
                    if (moves.incrementAndGet()
                            == INDEX_CHECKPOINT_MOVE) {
                        throw new IOException(
                                "injected index checkpoint failure");
                    }
                    Files.move(source, target,
                            StandardCopyOption
                                    .REPLACE_EXISTING);
                });
        final TreeReportSession session = renderer.start(
                TreeReportMetadata.from(result(List.of())),
                1, output);

        assertThatThrownBy(() -> session.publish(
                reactor("pom.xml", ReactorStatus.SUCCESS,
                        "", List.of())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(
                        "injected index checkpoint failure");

        assertThat(filenames(output)).hasSize(1);
        assertThat(output.resolve("index.html"))
                .content().contains("RUNNING")
                .contains("<td>0/1</td>")
                .doesNotContain("pom.xml");
        assertThat(session.getSummaries()).isEmpty();
    }

    @Test
    void newSessionClearsOldOwnedPagesAndPreservesRoot()
            throws Exception {
        final Path output = temporary.resolve("new-run");
        Files.createDirectories(output.resolve(
                "dependency-report/reactors"));
        Files.writeString(output.resolve("keep.txt"),
                "keep");
        Files.writeString(output.resolve(
                "dependency-report/reactors/stale.html"),
                "stale");
        Files.writeString(output.resolve("index.html"),
                "stale-index");

        new TreeReportRenderer().start(
                TreeReportMetadata.from(result(List.of())),
                FRESH_RUN_REACTORS, output);

        assertThat(output.resolve("keep.txt"))
                .hasContent("keep");
        assertThat(output.resolve(
                "dependency-report/reactors/stale.html"))
                .doesNotExist();
        assertThat(output.resolve("index.html"))
                .content().contains("RUNNING")
                .contains("<td>0/3</td>");
    }

    @Test
    void publishesLazyClassConflictShardsWithoutPhysicalPathsOrInlineSource()
            throws Exception {
        final Path secretProject = temporary.resolve("secret-project");
        final Path secretJar = temporary.resolve("secret-library.jar");
        final TreeClassConflictCandidate winner =
                new TreeClassConflictCandidate(CodeOrigin.PROJECT,
                        "demo:app:jar:1", "", "digest-project",
                        "sample/Duplicate.class", secretProject,
                        DecompiledMethod.available(
                                "package sample; class Duplicate {}"));
        final TreeClassConflictCandidate shadowed =
                new TreeClassConflictCandidate(CodeOrigin.DEPENDENCY,
                        "demo:library:jar:1", "compile", "digest-library",
                        "sample/Duplicate.class", secretJar,
                        DecompiledMethod.unavailable("fixture unavailable"));
        final TreeClassConflict conflict = new TreeClassConflict(
                "sample/Duplicate", ClassConflictRisk.HIGH, winner,
                List.of(winner, shadowed),
                "Current module target/classes precedence");
        final ModuleTreeResult module = module(
                "demo:app:1", List.of(), "")
                .withClassAnalysis(List.of(conflict), List.of());
        final Path output = temporary.resolve("class-conflicts");

        new TreeReportRenderer().render(result(List.of(reactor(
                "pom.xml", ReactorStatus.SUCCESS, "", List.of(module)))),
                output);

        final Path pagePath = onlyReactorPage(output);
        final String page = Files.readString(pagePath);
        assertThat(page)
                .contains("<h2>Module 分析</h2>")
                .contains("data-combobox=\"module-selector\"")
                .doesNotContain("sample.Duplicate", "HIGH")
                .doesNotContain("data-view-class-code")
                .doesNotContain("package sample; class Duplicate {}")
                .doesNotContain(secretProject.toString(), secretJar.toString());
        assertThat(output.resolve(
                "dependency-report/assets/report.css")).content()
                .contains(".source-switches button[aria-pressed=true]")
                .contains(".combobox-toggle[aria-expanded=true]")
                .contains(".dependency-filter-primary")
                .contains("@media(max-width:1100px)");
        assertThat(output.resolve(
                "dependency-report/assets/report.js")).content()
                .contains("setAttribute(\"aria-pressed\"")
                .contains("panel.replaceChildren(nav, pre)")
                .contains("recordsForIds(\"class-sources\"");
        final Path data = pageDataDirectory(pagePath);
        try (Stream<Path> files = Files.list(data)) {
            final List<Path> shards = files.toList();
            assertThat(shards.stream().map(value -> value.getFileName()
                            .toString()))
                    .anyMatch(value -> value.startsWith("class-conflicts-"))
                    .anyMatch(value -> value.startsWith("class-sources-"));
        }
        assertThat(allShardContent(data))
                .contains("sample.Duplicate", "HIGH", "sourceCode",
                        "package sample; class Duplicate {}")
                .doesNotContain(secretProject.toString(),
                        secretJar.toString());
        assertThat(output.resolve("index.html")).content()
                .contains("Class conflicts</th>")
                .contains("High-risk class conflicts</th>");
    }

    @Test
    void checkpointSummaryHasNoHeavyResultReferences() {
        assertThat(Arrays.stream(
                        ReactorReportSummary.class
                                .getDeclaredFields())
                .map(java.lang.reflect.Field::getType))
                .doesNotContain(ReactorTreeResult.class,
                        ModuleTreeResult.class,
                        DependencyOccurrence.class);
    }

    private DependencyOccurrence occurrence(
            final String groupId,
            final String artifactId,
            final boolean selected,
            final List<String> path) {
        return occurrence(groupId, artifactId,
                "1", "1", selected, "compile", path);
    }

    private DependencyOccurrence occurrence(
            final String groupId,
            final String artifactId,
            final String requestedVersion,
            final String selectedVersion,
            final boolean selected,
            final String scope,
            final List<String> path) {
        return new DependencyOccurrence(
                new DependencyKey(groupId, artifactId,
                        "jar", ""),
                new OccurrenceVersions(
                        requestedVersion, "",
                        requestedVersion, selectedVersion),
                new OccurrenceScopes(scope, ""),
                null,
                new OccurrenceSelection(selected,
                        selected ? "" : "conflict"),
                path, false);
    }

    private DependencyOccurrence managedOccurrence(
            final String groupId,
            final String artifactId,
            final OccurrenceVersions versions,
            final boolean selected,
            final String scope,
            final List<String> path) {
        return new DependencyOccurrence(
                new DependencyKey(groupId, artifactId,
                        "jar", ""),
                versions,
                new OccurrenceScopes(scope, ""),
                null,
                new OccurrenceSelection(selected,
                        selected ? "" : "conflict"),
                path, false);
    }

    private ModuleTreeResult module(
            final String coordinate,
            final List<DependencyOccurrence> occurrences,
            final String failure) {
        return module(coordinate, occurrences, failure,
                ModuleAnalysisRole.REQUESTED);
    }

    private ModuleTreeResult module(
            final String coordinate,
            final List<DependencyOccurrence> occurrences,
            final String failure,
            final ModuleAnalysisRole role) {
        return new ModuleTreeResult(
                Path.of(coordinate.replace(':', '-'),
                        "pom.xml"),
                coordinate, occurrences, true, failure,
                role);
    }

    private ReactorTreeResult reactor(
            final String id,
            final ReactorStatus status,
            final String reason,
            final List<ModuleTreeResult> modules) {
        final Path pom = Path.of(id);
        final ReactorDescriptor descriptor =
                new ReactorDescriptor(pom, "g:root:1",
                        List.of(pom), List.of());
        return new ReactorTreeResult(
                descriptor, modules, status, reason);
    }

    private TreeRepositoryResult result(
            final List<ReactorTreeResult> reactors) {
        final RepositorySnapshot snapshot =
                new RepositorySnapshot(temporary,
                        temporary, "current checkout",
                        "abc", "main", true, () -> { });
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeDescriptor(
                        MavenRuntimeSource.EMBEDDED,
                        temporary.resolve("mvn"),
                        MavenVersion.parse("3.6.3"),
                        null, temporary);
        return new TreeRepositoryResult(snapshot,
                runtime, List.of(), Set.of("compile"),
                new PreflightReport(List.of()), reactors);
    }

    private Path onlyReactorPage(final Path output)
            throws Exception {
        try (Stream<Path> pages = Files.list(
                output.resolve(
                        "dependency-report/reactors"))) {
            return pages.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".html"))
                    .findFirst().orElseThrow();
        }
    }

    private List<String> filenames(final Path output)
            throws Exception {
        try (Stream<Path> pages = Files.list(
                output.resolve(
                        "dependency-report/reactors"))) {
            final List<String> result = new ArrayList<>();
            pages.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted().forEach(result::add);
            return result;
        }
    }

    private Path pageDataDirectory(final Path page) {
        final String filename = page.getFileName().toString();
        return page.resolveSibling(filename.substring(
                0, filename.length() - ".html".length()) + "-data");
    }

    private String allShardContent(final Path directory) throws Exception {
        final StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .sorted().toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }
}
