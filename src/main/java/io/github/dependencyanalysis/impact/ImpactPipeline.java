package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.BuildRunner;
import io.github.dependencyanalysis.bytecode
        .BytecodeDiffEngine;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.bytecode
        .MethodBodyDecompiler;
import io.github.dependencyanalysis.bytecode
        .MethodBodyEvidence;
import io.github.dependencyanalysis.callgraph.CallGraph;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph
        .CallGraphEngine;
import io.github.dependencyanalysis.callgraph
        .JdkAnalysisStage;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.dependency
        .ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency
        .DependencyAnalyzer;
import io.github.dependencyanalysis.dependency
        .DependencyChange;
import io.github.dependencyanalysis.dependency
        .DependencyDiffEngine;
import io.github.dependencyanalysis.dependency
        .ModuleDependencyTree;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticCollector;
import io.github.dependencyanalysis.jar.JarLocationResult;
import io.github.dependencyanalysis.jar.JarLocator;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.report.ReportGenerator;
import io.github.dependencyanalysis.report
        .ImpactReportMetadata;
import io.github.dependencyanalysis.runtime
        .JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.workspace
        .WorkspaceResult;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - impact pipeline
/** Executes the migrated change impact pipeline. */
final class ImpactPipeline {

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /** Included change kinds. */
    private final Set<ChangePointKind> kinds;

    /** Maven runtime. */
    private final MavenRuntimeDescriptor runtime;

    /** Maven arguments. */
    private final List<String> mavenArguments;

    /** Target Java runtime. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Call Graph timeout in seconds. */
    private final long callGraphTimeoutSeconds;

    /** Command-owned temporary directory. */
    private final Path temporaryDirectory;

    /**
     * Creates a pipeline.
     *
     * @param collector diagnostics
     * @param includedKinds included change kinds
     * @param selectedRuntime selected runtime
     * @param arguments safe Maven arguments
     * @param targetJavaRuntime target Java runtime
     * @param timeoutSeconds Call Graph timeout
     */
    ImpactPipeline(
            final DiagnosticCollector collector,
            final Set<ChangePointKind> includedKinds,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments,
            final JavaRuntimeDescriptor targetJavaRuntime,
            final long timeoutSeconds) {
        this(collector, includedKinds, selectedRuntime,
                arguments, targetJavaRuntime,
                timeoutSeconds, null);
    }

    /**
     * Creates a pipeline using command-owned temporary storage.
     *
     * @param collector diagnostics
     * @param includedKinds included change kinds
     * @param selectedRuntime selected runtime
     * @param arguments safe Maven arguments
     * @param targetJavaRuntime target Java runtime
     * @param timeoutSeconds Call Graph timeout
     * @param tempDirectory command temporary directory
     */
    ImpactPipeline(
            final DiagnosticCollector collector,
            final Set<ChangePointKind> includedKinds,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments,
            final JavaRuntimeDescriptor targetJavaRuntime,
            final long timeoutSeconds,
            final Path tempDirectory) {
        diagnostics = collector;
        kinds = Set.copyOf(includedKinds);
        runtime = selectedRuntime;
        mavenArguments = List.copyOf(arguments);
        javaRuntime = Objects.requireNonNull(
                targetJavaRuntime,
                "targetJavaRuntime");
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException(
                    "timeoutSeconds must be >= 0");
        }
        callGraphTimeoutSeconds = timeoutSeconds;
        temporaryDirectory = tempDirectory;
    }

    /**
     * Runs the complete impact pipeline.
     *
     * @param workspace prepared workspace
     * @param output output file
     * @param format output format
     * @param preflight canonical preflight report
     * @throws Exception on pipeline failure
     */
    void run(
            final WorkspaceResult workspace,
            final File output,
            final OutputFormat format,
            final PreflightReport preflight)
            throws Exception {
        diagnostics.startStage("pipeline");
        try {
            final File javaHome =
                    runtime.getJavaHome() == null
                            ? null
                            : runtime.getJavaHome()
                            .toFile();
            new BuildRunner("baseline",
                    workspace.getBaseline().getPath(),
                    diagnostics, javaHome,
                    runtime.getExecutable(),
                    mavenArguments,
                    temporaryDirectory).build();
            final BuildResult targetBuild =
                    new BuildRunner("target",
                            workspace.getTarget()
                                    .getPath(),
                            diagnostics, javaHome,
                            runtime.getExecutable(),
                            mavenArguments,
                            temporaryDirectory).build();
            final List<ModuleDependencyTree>
                    baselineTrees =
                    new DependencyAnalyzer(
                            "baseline",
                            workspace.getBaseline()
                                    .getPath(),
                            Collections.emptySet(),
                            diagnostics, javaHome,
                            runtime.getExecutable(),
                            mavenArguments)
                            .withTemporaryDirectory(
                                    temporaryDirectory)
                            .analyze();
            final Set<ArtifactCoord> reactor =
                    extractReactor(baselineTrees);
            final List<ModuleDependencyTree>
                    targetTrees =
                    new DependencyAnalyzer(
                            "target",
                            workspace.getTarget()
                                    .getPath(),
                            reactor, diagnostics,
                            javaHome,
                            runtime.getExecutable(),
                            mavenArguments)
                            .withTemporaryDirectory(
                                    temporaryDirectory)
                            .analyze();
            final List<DependencyChange> changes =
                    new DependencyDiffEngine().diff(
                            baselineTrees, targetTrees);
            diagnostics.info("pipeline",
                    "Dependency changes: "
                            + changes.size());
            final ChangePointAnalysis analysis =
                    computeChangePoints(changes);
            final List<ChangePoint> points =
                    analysis.getPoints();
            diagnostics.info("pipeline",
                    "Change points: "
                            + points.size());
            logChangePointBreakdown(points);
            final ImpactResult impact =
                    computeImpact(points,
                            targetBuild);
            final List<MethodBodyEvidence>
                    bodyEvidence =
                    computeMethodBodyEvidence(
                            impact, analysis);
            new ReportGenerator().generate(
                    changes, points, impact,
                    diagnostics.getEvents(),
                    new ImpactReportMetadata(
                            preflight, runtime,
                            bodyEvidence),
                    format, output.toPath());
        } finally {
            diagnostics.endStage("pipeline");
        }
    }

    private Set<ArtifactCoord> extractReactor(
            final List<ModuleDependencyTree> trees) {
        final Set<ArtifactCoord> result =
                new HashSet<>();
        for (ModuleDependencyTree tree : trees) {
            result.add(tree.getModule());
        }
        return result;
    }

    private ChangePointAnalysis computeChangePoints(
            final List<DependencyChange> changes)
            throws Exception {
        final List<DependencyChange> changed =
                new ArrayList<>();
        for (DependencyChange item : changes) {
            if (item.getChangeType()
                    == ChangeType.VERSION_CHANGED) {
                changed.add(item);
            }
        }
        if (changed.isEmpty()) {
            return new ChangePointAnalysis(
                    Collections.emptyList(),
                    Collections.emptyMap());
        }
        final List<JarLocationResult> jars =
                new JarLocator().locate(changed);
        final BytecodeDiffEngine engine =
                new BytecodeDiffEngine(kinds);
        final List<ChangePoint> result =
                new ArrayList<>();
        final Map<ChangePoint,
                JarLocationResult> locations =
                new LinkedHashMap<>();
        for (JarLocationResult jar : jars) {
            final List<ChangePoint> jarPoints =
                    engine.diff(jar);
            result.addAll(jarPoints);
            for (ChangePoint point : jarPoints) {
                locations.put(point, jar);
            }
        }
        return new ChangePointAnalysis(
                result, locations);
    }

    private List<MethodBodyEvidence>
            computeMethodBodyEvidence(
            final ImpactResult impact,
            final ChangePointAnalysis analysis) {
        final List<ChangePoint> requested =
                impactedBodyChanges(impact);
        if (requested.isEmpty()) {
            return Collections.emptyList();
        }
        diagnostics.startStage(
                MethodBodyDecompiler.STAGE);
        try {
            final MethodBodyDecompiler decompiler =
                    new MethodBodyDecompiler(
                            diagnostics);
            final List<MethodBodyEvidence> result =
                    new ArrayList<>();
            for (ChangePoint point : requested) {
                final JarLocationResult location =
                        analysis.locationOf(point);
                if (location == null) {
                    throw new IllegalStateException(
                            "Jar provenance missing for "
                                    + point);
                }
                result.add(decompiler.decompile(
                        location, point));
            }
            diagnostics.info(
                    MethodBodyDecompiler.STAGE,
                    "Method body evidence: "
                            + result.size());
            return Collections
                    .unmodifiableList(result);
        } finally {
            diagnostics.endStage(
                    MethodBodyDecompiler.STAGE);
        }
    }

    static List<ChangePoint> impactedBodyChanges(
            final ImpactResult impact) {
        final Set<ChangePoint> unique =
                new LinkedHashSet<>();
        for (ImpactPath path : impact.getPaths()) {
            final ChangePoint point =
                    path.getChangePoint();
            if (point.getKind()
                    == ChangePointKind
                    .METHOD_BODY_CHANGED) {
                unique.add(point);
            }
        }
        final List<ChangePoint> requested =
                new ArrayList<>(unique);
        requested.sort(changePointComparator());
        return Collections.unmodifiableList(
                requested);
    }

    private static Comparator<ChangePoint>
            changePointComparator() {
        return Comparator
                .comparing((ChangePoint point) ->
                        point.getArtifact()
                                .toString())
                .thenComparing(
                        ChangePoint::getOwner)
                .thenComparing(point ->
                        point.getName() == null
                                ? ""
                                : point.getName())
                .thenComparing(point ->
                        point.getDescriptor() == null
                                ? ""
                                : point.getDescriptor());
    }

    private ImpactResult computeImpact(
            final List<ChangePoint> points,
            final BuildResult targetBuild) {
        if (points.isEmpty()) {
            return new ImpactResult(
                    Collections.emptyList(),
                    new EnumMap<>(
                            NotReportedReason.class));
        }
        diagnostics.startStage("impact-seed");
        final Map<ChangePoint, Set<MethodId>> seeds;
        try {
            seeds = new ChangePointRefScanner()
                    .scan(points, targetBuild);
            diagnostics.info("impact-seed",
                    "Seeds resolved: " + countSeeds(seeds));
            diagnostics.endStage("impact-seed");
        } catch (RuntimeException exception) {
            diagnostics.failStage("impact-seed",
                    exception.getMessage());
            throw exception;
        }
        final ImpactTracer tracer =
                new ImpactTracer(diagnostics);
        if (countSeeds(seeds) == 0) {
            diagnostics.info("pipeline",
                    "No impact seed; skipping JDK analysis,"
                            + " CHA and RTA");
            return tracer.traceWithoutCallGraph(
                    points, seeds);
        }
        final CallGraph callGraph =
                new CallGraphEngine(diagnostics)
                        .build(targetBuild,
                                new JdkAnalysisStage(
                                        diagnostics)
                                        .prepare(javaRuntime),
                                callGraphTimeoutSeconds);
        return tracer.trace(points, callGraph,
                targetBuild, seeds);
    }

    private int countSeeds(
            final Map<ChangePoint, ? extends Set<?>> seeds) {
        int total = 0;
        for (Set<?> values : seeds.values()) {
            total += values.size();
        }
        return total;
    }

    private void logChangePointBreakdown(
            final List<ChangePoint> points) {
        final Map<ChangePointKind, Integer>
                counts = new EnumMap<>(
                ChangePointKind.class);
        for (ChangePointKind kind
                : ChangePointKind.values()) {
            counts.put(kind, 0);
        }
        for (ChangePoint point : points) {
            counts.merge(point.getKind(),
                    1, Integer::sum);
        }
        for (ChangePointKind kind
                : ChangePointKind.values()) {
            if (counts.get(kind) > 0) {
                diagnostics.info("pipeline",
                        "  " + kind + ": "
                                + counts.get(kind));
            }
        }
    }
}
