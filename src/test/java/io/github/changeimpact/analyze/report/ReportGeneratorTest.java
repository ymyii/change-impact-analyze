package io.github.changeimpact.analyze.report;

import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.bytecode.ChangePointKind;
import io.github.changeimpact.analyze.callgraph.CallEdge;
import io.github.changeimpact.analyze.callgraph.EdgeKind;
import io.github.changeimpact.analyze.callgraph.MethodId;
import io.github.changeimpact.analyze.cli.OutputFormat;
import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyChange;
import io.github.changeimpact.analyze.dependency.DependencyScope;
import io.github.changeimpact.analyze.diagnostic.DiagnosticEvent;
import io.github.changeimpact.analyze.diagnostic.DiagnosticLevel;
import io.github.changeimpact.analyze.impact.ImpactPath;
import io.github.changeimpact.analyze.impact.ImpactResult;
import io.github.changeimpact.analyze.impact.NotReportedReason;

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
     * HTML format generates file.
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
        final String content =
                Files.readString(output);
        assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("<style>")
                .contains("</html>");
    }

    /**
     * Markdown format generates file.
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
        final String content =
                Files.readString(output);
        assertThat(content)
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
                Files.readString(output);
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
                Files.readString(output);
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
        final String content =
                Files.readString(output);
        assertThat(content)
                .contains("VERSION_CHANGED")
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
                Files.readString(output);
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
                Files.readString(output);
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
                Files.readString(output);
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
