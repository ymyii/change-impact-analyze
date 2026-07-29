package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.CallEdge;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactResult;
import io.github.dependencyanalysis.impact.NotReportedReason;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReportGenerator.
 */
class ReportGeneratorTest {

    /** Sample elapsed millis. */
    private static final long SAMPLE_ELAPSED =
            5L;

    /** Temp output directory. */
    @TempDir
    private Path tempDir;

    /** Test generator. */
    private final ReportGenerator
            generator = new ReportGenerator();

    /**
     * HTML format generates index
     * and 3 sub-files.
     *
     * @throws Exception on error
     */
    @Test
    void htmlFormatGeneratesFile()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        assertThat(output).exists();
        assertThat(tempDir.resolve(
                "report-dependencies.html"))
                .exists();
        assertThat(tempDir.resolve(
                "report-internal-changes.html"))
                .exists();
        assertThat(tempDir.resolve(
                "report-impact-paths.html"))
                .exists();
        final String index =
                Files.readString(output);
        assertThat(index)
                .contains("<!DOCTYPE html>")
                .contains("<style>")
                .contains("</html>")
                .contains("Summary")
                .contains("Diagnostics");
    }

    /**
     * Markdown format generates index
     * and 3 sub-files.
     *
     * @throws Exception on error
     */
    @Test
    void markdownFormatGeneratesFile()
            throws Exception {
        final Path output = tempDir
                .resolve("report.md");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.MD,
                output);
        assertThat(output).exists();
        assertThat(tempDir.resolve(
                "report-dependencies.md"))
                .exists();
        assertThat(tempDir.resolve(
                "report-internal-changes.md"))
                .exists();
        assertThat(tempDir.resolve(
                "report-impact-paths.md"))
                .exists();
        final String index =
                Files.readString(output);
        assertThat(index)
                .contains("# Impact")
                .contains("## Summary");
    }

    /**
     * Added dependency shown in
     * changes section.
     *
     * @throws Exception on error
     */
    @Test
    void addedDependencyInChanges()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final List<DependencyChange>
                changes = new ArrayList<>();
        changes.add(new DependencyChange(
                ChangeType.ADDED,
                null,
                new ArtifactCoord("g", "a",
                        "jar", "1.0"),
                DependencyScope.COMPILE,
                "module1"));
        generator.generate(changes,
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String content =
                Files.readString(tempDir
                        .resolve("report-dependencies.html"));
        assertThat(content)
                .contains("ADDED")
                .contains("g:a:1.0");
    }

    /**
     * Removed dependency shown in
     * changes section.
     *
     * @throws Exception on error
     */
    @Test
    void removedDependencyInChanges()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final List<DependencyChange>
                changes = new ArrayList<>();
        changes.add(new DependencyChange(
                ChangeType.REMOVED,
                new ArtifactCoord("g", "a",
                        "jar", "1.0"),
                null,
                DependencyScope.COMPILE,
                "module1"));
        generator.generate(changes,
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String content =
                Files.readString(tempDir
                        .resolve("report-dependencies.html"));
        assertThat(content)
                .contains("REMOVED");
    }

    /**
     * Version changed shows change
     * points.
     *
     * @throws Exception on error
     */
    @Test
    void versionChangedShowsPoints()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final List<DependencyChange>
                changes = new ArrayList<>();
        final ArtifactCoord oldArt =
                new ArtifactCoord("g", "a",
                        "jar", "1.0");
        final ArtifactCoord newArt =
                new ArtifactCoord("g", "a",
                        "jar", "2.0");
        changes.add(new DependencyChange(
                ChangeType.VERSION_CHANGED,
                oldArt, newArt,
                DependencyScope.COMPILE,
                "module1"));
        final List<ChangePoint> points =
                new ArrayList<>();
        points.add(new ChangePoint(newArt,
                ChangePointKind.METHOD_REMOVED,
                "com/Foo", "bar",
                "()V", null, null));
        generator.generate(changes, points,
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String depContent =
                Files.readString(tempDir
                        .resolve("report-dependencies.html"));
        assertThat(depContent)
                .contains("VERSION_CHANGED");
        final String intContent =
                Files.readString(tempDir
                        .resolve("report-internal-changes.html"));
        assertThat(intContent)
                .contains("METHOD_REMOVED")
                .contains("com/Foo");
    }

    /**
     * Impact paths shown when present.
     *
     * @throws Exception on error
     */
    @Test
    void impactPathsShownWhenPresent()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final ArtifactCoord art =
                new ArtifactCoord("g", "a",
                        "jar", "1.0");
        final ChangePoint cp =
                new ChangePoint(art,
                        ChangePointKind
                                .METHOD_REMOVED,
                        "com/Foo", "bar",
                        "()V", null, null);
        final MethodId method =
                new MethodId("com/App",
                        "main",
                        "([Ljava/lang/String;)V",
                        "module1",
                        "com/App.class");
        final ImpactPath path =
                new ImpactPath(method, cp,
                        Collections.emptyList(),
                        new HashSet<>(),
                        Collections.emptyList());
        final ImpactResult result =
                new ImpactResult(
                        List.of(path),
                        new EnumMap<>(
                                NotReportedReason
                                        .class));
        generator.generate(
                Collections.emptyList(),
                List.of(cp), result,
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String content =
                Files.readString(tempDir
                        .resolve("report-impact-paths.html"));
        assertThat(content)
                .contains("Path #1")
                .contains("com/App.main");
    }

    /**
     * No impact shows message.
     *
     * @throws Exception on error
     */
    @Test
    void noImpactShowsMessage()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String content =
                Files.readString(tempDir
                        .resolve("report-internal-changes.html"));
        assertThat(content)
                .contains("No static")
                .contains("confirmed");
    }

    /**
     * Provided scope shows API risk.
     *
     * @throws Exception on error
     */
    @Test
    void providedScopeShowsApiRisk()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final List<DependencyChange>
                changes = new ArrayList<>();
        changes.add(new DependencyChange(
                ChangeType.VERSION_CHANGED,
                new ArtifactCoord("g", "a",
                        "jar", "1.0"),
                new ArtifactCoord("g", "a",
                        "jar", "2.0"),
                DependencyScope.PROVIDED,
                "module1"));
        generator.generate(changes,
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String content =
                Files.readString(tempDir
                        .resolve("report-dependencies.html"));
        assertThat(content)
                .contains("API risk")
                .contains("PROVIDED");
    }

    /**
     * HTML snapshot test verifies
     * deterministic output.
     */
    @Test
    void htmlSnapshotIsDeterministic() {
        final String first =
                generator.generateToString(
                        sampleChanges(),
                        sampleChangePoints(),
                        sampleImpactResult(),
                        sampleEvents(),
                        OutputFormat.HTML);
        final String second =
                generator.generateToString(
                        sampleChanges(),
                        sampleChangePoints(),
                        sampleImpactResult(),
                        sampleEvents(),
                        OutputFormat.HTML);
        final String n1 =
                normalizeTimestamp(first);
        final String n2 =
                normalizeTimestamp(second);
        assertThat(n1).isEqualTo(n2);
        assertThat(n1)
                .contains("<!DOCTYPE html>")
                .contains("Module: mod-a")
                .contains("Module: mod-b")
                .contains("VERSION_CHANGED")
                .contains("ADDED")
                .contains("REMOVED")
                .contains("METHOD_REMOVED")
                .contains("Path #1")
                .contains("API risk")
                .contains("Summary")
                .contains("Diagnostics");
    }

    /**
     * Markdown snapshot test verifies
     * deterministic output.
     */
    @Test
    void markdownSnapshotIsDeterministic() {
        final String first =
                generator.generateToString(
                        sampleChanges(),
                        sampleChangePoints(),
                        sampleImpactResult(),
                        sampleEvents(),
                        OutputFormat.MD);
        final String second =
                generator.generateToString(
                        sampleChanges(),
                        sampleChangePoints(),
                        sampleImpactResult(),
                        sampleEvents(),
                        OutputFormat.MD);
        final String n1 =
                normalizeTimestamp(first);
        final String n2 =
                normalizeTimestamp(second);
        assertThat(n1).isEqualTo(n2);
        assertThat(n1)
                .contains("# Impact Analysis Report")
                .contains("## Summary")
                .contains("## Dependency Changes")
                .contains("### Module: mod-a")
                .contains("### Module: mod-b")
                .contains("## Internal Changes")
                .contains("## Impact Paths")
                .contains("## Diagnostics")
                .contains("METHOD_REMOVED")
                .contains("API risk")
                .contains("Path #1");
    }

    /**
     * Module grouping test verifies
     * changes are grouped by module.
     */
    @Test
    void htmlGroupsChangesByModule() {
        final String html =
                generator.generateToString(
                        sampleChanges(),
                        Collections.emptyList(),
                        emptyResult(),
                        Collections.emptyList(),
                        OutputFormat.HTML);
        assertThat(html)
                .contains("Module: mod-a")
                .contains("Module: mod-b");
        final int idxA =
                html.indexOf("Module: mod-a");
        final int idxB =
                html.indexOf("Module: mod-b");
        assertThat(idxA).isGreaterThan(0);
        assertThat(idxB).isGreaterThan(idxA);
    }

    /**
     * Markdown module grouping test.
     */
    @Test
    void mdGroupsChangesByModule() {
        final String md =
                generator.generateToString(
                        sampleChanges(),
                        Collections.emptyList(),
                        emptyResult(),
                        Collections.emptyList(),
                        OutputFormat.MD);
        assertThat(md)
                .contains("### Module: mod-a")
                .contains("### Module: mod-b");
    }

    /**
     * Sub-file naming convention test
     * with index link cross-validation.
     *
     * @throws Exception on error
     */
    @Test
    void subFileNamingConvention()
            throws Exception {
        final Path output = tempDir
                .resolve("my-report.html");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String dep =
                "my-report-dependencies.html";
        final String intf =
                "my-report-internal-changes"
                        + ".html";
        final String imp =
                "my-report-impact-paths.html";
        assertThat(tempDir.resolve(dep))
                .exists();
        assertThat(tempDir.resolve(intf))
                .exists();
        assertThat(tempDir.resolve(imp))
                .exists();
        final String index =
                Files.readString(output);
        assertThat(index)
                .contains("href=\"" + dep + "\"")
                .contains("href=\"" + intf + "\"")
                .contains("href=\"" + imp + "\"");
    }

    /**
     * HTML sub-files content test.
     *
     * @throws Exception on error
     */
    @Test
    void htmlSubFilesContent()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        final ArtifactCoord art =
                new ArtifactCoord("g", "a",
                        "jar", "1.0");
        final ChangePoint cp =
                new ChangePoint(art,
                        ChangePointKind
                                .METHOD_REMOVED,
                        "com/Foo", "bar",
                        "()V", null, null);
        final MethodId method =
                new MethodId("com/App",
                        "main",
                        "([Ljava/lang/String;)V",
                        "module1",
                        "com/App.class");
        final ImpactPath path =
                new ImpactPath(method, cp,
                        Collections.emptyList(),
                        new HashSet<>(),
                        Collections.emptyList());
        final ImpactResult result =
                new ImpactResult(
                        List.of(path),
                        new EnumMap<>(
                                NotReportedReason
                                        .class));
        final List<DependencyChange> changes =
                new ArrayList<>();
        changes.add(new DependencyChange(
                ChangeType.ADDED,
                null,
                new ArtifactCoord("g", "a",
                        "jar", "1.0"),
                DependencyScope.COMPILE,
                "module1"));
        generator.generate(changes,
                List.of(cp), result,
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String dep = Files.readString(
                tempDir.resolve(
                        "report-dependencies.html"));
        assertThat(dep)
                .contains("<!DOCTYPE html>")
                .contains("<style>")
                .contains("ADDED")
                .contains("g:a:1.0");
        final String intF = Files.readString(
                tempDir.resolve(
                        "report-internal-changes.html"));
        assertThat(intF)
                .contains("<!DOCTYPE html>")
                .contains("METHOD_REMOVED")
                .contains("com/Foo");
        final String imp = Files.readString(
                tempDir.resolve(
                        "report-impact-paths.html"));
        assertThat(imp)
                .contains("<!DOCTYPE html>")
                .contains("Path #1")
                .contains("com/App.main");
    }

    /**
     * Markdown sub-files content test.
     *
     * @throws Exception on error
     */
    @Test
    void mdSubFilesContent()
            throws Exception {
        final Path output = tempDir
                .resolve("report.md");
        final ArtifactCoord art =
                new ArtifactCoord("g", "a",
                        "jar", "1.0");
        final ChangePoint cp =
                new ChangePoint(art,
                        ChangePointKind
                                .METHOD_REMOVED,
                        "com/Foo", "bar",
                        "()V", null, null);
        final MethodId method =
                new MethodId("com/App",
                        "main",
                        "([Ljava/lang/String;)V",
                        "module1",
                        "com/App.class");
        final ImpactPath path =
                new ImpactPath(method, cp,
                        Collections.emptyList(),
                        new HashSet<>(),
                        Collections.emptyList());
        final ImpactResult result =
                new ImpactResult(
                        List.of(path),
                        new EnumMap<>(
                                NotReportedReason
                                        .class));
        final List<DependencyChange> changes =
                new ArrayList<>();
        changes.add(new DependencyChange(
                ChangeType.ADDED,
                null,
                new ArtifactCoord("g", "a",
                        "jar", "1.0"),
                DependencyScope.COMPILE,
                "module1"));
        generator.generate(changes,
                List.of(cp), result,
                Collections.emptyList(),
                OutputFormat.MD,
                output);
        final String dep = Files.readString(
                tempDir.resolve(
                        "report-dependencies.md"));
        assertThat(dep)
                .contains("# Dependency Changes")
                .contains("ADDED")
                .contains("|");
        assertThat(dep)
                .doesNotContain("<html>")
                .doesNotContain("<style>")
                .doesNotContain("<table>");
        final String intF = Files.readString(
                tempDir.resolve(
                        "report-internal-changes.md"));
        assertThat(intF)
                .contains("# Internal Changes")
                .contains("METHOD_REMOVED")
                .contains("|");
        assertThat(intF)
                .doesNotContain("<html>")
                .doesNotContain("<table>");
        final String imp = Files.readString(
                tempDir.resolve(
                        "report-impact-paths.md"));
        assertThat(imp)
                .contains("# Impact Paths")
                .contains("Path #1")
                .contains("**Affected:**");
        assertThat(imp)
                .doesNotContain("<html>")
                .doesNotContain("<ol>")
                .doesNotContain("<li>");
    }

    /**
     * Index contains summary links
     * that match actual sub-files.
     *
     * @throws Exception on error
     */
    @Test
    void indexContainsSummaryLinks()
            throws Exception {
        final Path output = tempDir
                .resolve("report.html");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.HTML,
                output);
        final String index =
                Files.readString(output);
        final String dep =
                "report-dependencies.html";
        final String intf =
                "report-internal-changes"
                        + ".html";
        final String imp =
                "report-impact-paths.html";
        assertThat(index)
                .contains("details")
                .contains("href=\"" + dep + "\"")
                .contains("href=\"" + intf + "\"")
                .contains("href=\"" + imp + "\"");
        assertThat(tempDir.resolve(dep))
                .exists();
        assertThat(tempDir.resolve(intf))
                .exists();
        assertThat(tempDir.resolve(imp))
                .exists();
    }

    /**
     * MD index links match actual
     * sub-files.
     *
     * @throws Exception on error
     */
    @Test
    void mdIndexLinksMatchSubFiles()
            throws Exception {
        final Path output = tempDir
                .resolve("report.md");
        generator.generate(
                Collections.emptyList(),
                Collections.emptyList(),
                emptyResult(),
                Collections.emptyList(),
                OutputFormat.MD,
                output);
        final String index =
                Files.readString(output);
        final String dep =
                "report-dependencies.md";
        final String intf =
                "report-internal-changes.md";
        final String imp =
                "report-impact-paths.md";
        assertThat(index)
                .contains("[details](" + dep + ")")
                .contains("[details](" + intf + ")")
                .contains("[details](" + imp + ")");
        assertThat(tempDir.resolve(dep))
                .exists();
        assertThat(tempDir.resolve(intf))
                .exists();
        assertThat(tempDir.resolve(imp))
                .exists();
    }

    /**
     * Creates empty impact result.
     *
     * @return empty result
     */
    private ImpactResult emptyResult() {
        return new ImpactResult(
                Collections.emptyList(),
                new EnumMap<>(
                        NotReportedReason
                                .class));
    }

    /**
     * Normalizes timestamp in content.
     *
     * @param content raw content
     * @return normalized content
     */
    private static String normalizeTimestamp(
            final String content) {
        return content.replaceAll(
                "Generated: \\d{4}-\\d{2}-\\d{2}"
                        + " \\d{2}:\\d{2}:\\d{2}",
                "Generated: TIMESTAMP");
    }

    /**
     * Builds sample dependency changes
     * across two modules.
     *
     * @return list of changes
     */
    private List<DependencyChange>
            sampleChanges() {
        final List<DependencyChange> list =
                new ArrayList<>();
        list.add(new DependencyChange(
                ChangeType.VERSION_CHANGED,
                new ArtifactCoord("org.example",
                        "lib-core", "jar", "1.0"),
                new ArtifactCoord("org.example",
                        "lib-core", "jar", "2.0"),
                DependencyScope.COMPILE,
                "mod-a"));
        list.add(new DependencyChange(
                ChangeType.ADDED,
                null,
                new ArtifactCoord("org.example",
                        "lib-new", "jar", "1.0"),
                DependencyScope.COMPILE,
                "mod-a"));
        list.add(new DependencyChange(
                ChangeType.VERSION_CHANGED,
                new ArtifactCoord("org.example",
                        "lib-api", "jar", "1.0"),
                new ArtifactCoord("org.example",
                        "lib-api", "jar", "1.1"),
                DependencyScope.PROVIDED,
                "mod-b"));
        list.add(new DependencyChange(
                ChangeType.REMOVED,
                new ArtifactCoord("org.example",
                        "lib-old", "jar", "1.0"),
                null,
                DependencyScope.COMPILE,
                "mod-b"));
        return list;
    }

    /**
     * Builds sample change points.
     *
     * @return list of change points
     */
    private List<ChangePoint>
            sampleChangePoints() {
        final List<ChangePoint> list =
                new ArrayList<>();
        list.add(new ChangePoint(
                new ArtifactCoord("org.example",
                        "lib-core", "jar", "2.0"),
                ChangePointKind.METHOD_REMOVED,
                "org/example/Foo", "bar",
                "()V", null, null));
        return list;
    }

    /**
     * Builds sample impact result
     * with one impact path.
     *
     * @return impact result
     */
    private ImpactResult sampleImpactResult() {
        final ArtifactCoord art =
                new ArtifactCoord(
                        "org.example",
                        "lib-core", "jar", "2.0");
        final ChangePoint cp =
                new ChangePoint(art,
                        ChangePointKind
                                .METHOD_REMOVED,
                        "org/example/Foo",
                        "bar", "()V",
                        null, null);
        final MethodId affected =
                new MethodId(
                        "com/app/Main",
                        "run", "()V",
                        "mod-a",
                        "com/app/Main.class");
        final MethodId callee =
                new MethodId(
                        "com/app/Service",
                        "process", "()V",
                        "mod-a",
                        "com/app/Service.class");
        final CallEdge edge =
                new CallEdge(affected, callee,
                        EdgeKind.INVOKE_VIRTUAL,
                        "INVOKE_VIRTUAL");
        final Set<String> mods =
                new LinkedHashSet<>();
        mods.add("mod-a");
        final ImpactPath path =
                new ImpactPath(affected, cp,
                        List.of(edge), mods,
                        Collections.emptyList());
        return new ImpactResult(
                List.of(path),
                new EnumMap<>(
                        NotReportedReason
                                .class));
    }

    /**
     * Builds sample diagnostic events.
     *
     * @return list of events
     */
    private List<DiagnosticEvent>
            sampleEvents() {
        final List<DiagnosticEvent> list =
                new ArrayList<>();
        list.add(new DiagnosticEvent.Builder()
                .stage("validation")
                .level(DiagnosticLevel.INFO)
                .message("Stage started: validation")
                .elapsedMillis(0L)
                .build());
        list.add(new DiagnosticEvent.Builder()
                .stage("validation")
                .level(DiagnosticLevel.INFO)
                .message("Stage ended: validation")
                .elapsedMillis(SAMPLE_ELAPSED)
                .build());
        return list;
    }
}
