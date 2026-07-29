package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.BuildRunner;
import io.github.dependencyanalysis.bytecode
        .BytecodeDiffEngine;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.callgraph.CallGraph;
import io.github.dependencyanalysis.callgraph
        .CallGraphEngine;
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
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.workspace
        .WorkspaceResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Creates a pipeline.
     *
     * @param collector diagnostics
     * @param includedKinds included change kinds
     * @param selectedRuntime selected runtime
     * @param arguments safe Maven arguments
     */
    ImpactPipeline(
            final DiagnosticCollector collector,
            final Set<ChangePointKind> includedKinds,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments) {
        diagnostics = collector;
        kinds = Set.copyOf(includedKinds);
        runtime = selectedRuntime;
        mavenArguments = List.copyOf(arguments);
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
                    mavenArguments).build();
            final BuildResult targetBuild =
                    new BuildRunner("target",
                            workspace.getTarget()
                                    .getPath(),
                            diagnostics, javaHome,
                            runtime.getExecutable(),
                            mavenArguments).build();
            final List<ModuleDependencyTree>
                    baselineTrees =
                    new DependencyAnalyzer(
                            "baseline",
                            workspace.getBaseline()
                                    .getPath(),
                            Collections.emptySet(),
                            diagnostics, javaHome,
                            runtime.getExecutable(),
                            mavenArguments).analyze();
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
                            mavenArguments).analyze();
            final List<DependencyChange> changes =
                    new DependencyDiffEngine().diff(
                            baselineTrees, targetTrees);
            diagnostics.info("pipeline",
                    "Dependency changes: "
                            + changes.size());
            final List<ChangePoint> points =
                    computeChangePoints(changes);
            diagnostics.info("pipeline",
                    "Change points: "
                            + points.size());
            logChangePointBreakdown(points);
            final ImpactResult impact =
                    computeImpact(points,
                            targetBuild);
            new ReportGenerator().generate(
                    changes, points, impact,
                    diagnostics.getEvents(),
                    new ImpactReportMetadata(
                            preflight, runtime),
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

    private List<ChangePoint> computeChangePoints(
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
            return Collections.emptyList();
        }
        final List<JarLocationResult> jars =
                new JarLocator().locate(changed);
        final BytecodeDiffEngine engine =
                new BytecodeDiffEngine(kinds);
        final List<ChangePoint> result =
                new ArrayList<>();
        for (JarLocationResult jar : jars) {
            result.addAll(engine.diff(jar));
        }
        return result;
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
        final CallGraph callGraph =
                new CallGraphEngine(diagnostics)
                        .build(targetBuild);
        return new ImpactTracer(diagnostics)
                .trace(points, callGraph,
                        targetBuild);
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
