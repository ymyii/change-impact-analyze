package io.github.changeimpact.analyze.cli;

import io.github.changeimpact.analyze.build.BuildResult;
import io.github.changeimpact.analyze.build.BuildRunner;
import io.github.changeimpact.analyze.bytecode.BytecodeDiffEngine;
import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.callgraph.CallGraph;
import io.github.changeimpact.analyze.callgraph.CallGraphEngine;
import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyAnalyzer;
import io.github.changeimpact.analyze.dependency.DependencyChange;
import io.github.changeimpact.analyze.dependency.DependencyDiffEngine;
import io.github.changeimpact.analyze.dependency.ModuleDependencyTree;
import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import io.github.changeimpact.analyze.impact.ImpactResult;
import io.github.changeimpact.analyze.impact.ImpactTracer;
import io.github.changeimpact.analyze.impact.NotReportedReason;
import io.github.changeimpact.analyze.jar.JarLocator;
import io.github.changeimpact.analyze.jar.JarLocationResult;
import io.github.changeimpact.analyze.report.ReportGenerator;
import io.github.changeimpact.analyze.workspace.WorkspaceManager;
import io.github.changeimpact.analyze.workspace.WorkspaceResult;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// Wiki: wiki/project/project-overview.md - CLI 主入口，picocli 命令定义
/**
 * CLI entry point for change impact analysis.
 */
@Command(
        name = "change-impact-analyze",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Analyze the impact of changes in a codebase."
)
public final class ChangeImpactAnalyzeCli
        implements Callable<Integer> {

    /** Project directory path. */
    @Option(
            names = "--project",
            description = "Path to the project directory.",
            required = true
    )
    private File project;

    /** Baseline branch or ref. */
    @Option(
            names = "--baseline",
            description = "Baseline branch or ref.",
            required = true
    )
    private String baseline;

    /** Target branch or ref. */
    @Option(
            names = "--target",
            description = "Target branch or ref."
    )
    private String target;

    /** Output file path. */
    @Option(
            names = "--output",
            description = "Output file path.",
            required = true
    )
    private File output;

    /** Output format. */
    @Option(
            names = "--format",
            description = "Output format: html or md.",
            defaultValue = "HTML"
    )
    private OutputFormat format;

    /** Diagnostic event collector. */
    private final DiagnosticCollector diagnostics;

    /**
     * Creates CLI with default diagnostics.
     */
    public ChangeImpactAnalyzeCli() {
        this.diagnostics = new DiagnosticCollector();
    }

    ChangeImpactAnalyzeCli(
            final DiagnosticCollector collector) {
        this.diagnostics = collector;
    }

    @Override
    public Integer call() {
        diagnostics.startStage("validation");

        if (!project.exists()) {
            diagnostics.failStage("validation",
                    "Project path does not"
                            + " exist: " + project);
            System.err.println(
                    "Project path does not"
                            + " exist: " + project);
            return 1;
        }

        if (!project.isDirectory()) {
            diagnostics.failStage("validation",
                    "Project path is not a"
                            + " directory: " + project);
            System.err.println(
                    "Project path is not a"
                            + " directory: " + project);
            return 1;
        }

        if (baseline == null
                || baseline.isBlank()) {
            diagnostics.failStage("validation",
                    "Baseline must not be blank");
            System.err.println(
                    "Baseline must not be blank");
            return 1;
        }

        final File parent =
                output.getAbsoluteFile()
                        .getParentFile();
        if (parent == null || !parent.exists()
                || !parent.canWrite()) {
            diagnostics.failStage("validation",
                    "Output parent dir missing"
                            + " or not writable: "
                            + output);
            System.err.println(
                    "Output parent dir missing"
                            + " or not writable: "
                            + output);
            return 1;
        }

        diagnostics.endStage("validation");

        try {
            runPipeline();
            return 0;
        } catch (Exception e) {
            diagnostics.error("pipeline",
                    "Pipeline failed: "
                            + e.getMessage());
            System.err.println(
                    "Pipeline failed: "
                            + e.getMessage());
            return 2;
        }
    }

    /**
     * Runs the full analysis pipeline.
     *
     * @throws Exception if any
     *  pipeline step fails
     */
    private void runPipeline()
            throws Exception {
        final java.nio.file.Path projPath =
                project.toPath();
        diagnostics.startStage("pipeline");
        try (WorkspaceManager wsMgr =
                new WorkspaceManager(
                        projPath, diagnostics)) {
            final WorkspaceResult ws =
                    wsMgr.prepare(baseline, target);

            final BuildResult baseBuild =
                    new BuildRunner("baseline",
                            ws.getBaseline().getPath(),
                            diagnostics).build();
            final BuildResult tgtBuild =
                    new BuildRunner("target",
                            ws.getTarget().getPath(),
                            diagnostics).build();

            final List<ModuleDependencyTree>
                    baseTrees =
                    new DependencyAnalyzer("baseline",
                            ws.getBaseline().getPath(),
                            Collections.emptySet(),
                            diagnostics).analyze();

            final Set<ArtifactCoord> reactor =
                    extractReactor(baseTrees);
            diagnostics.info("pipeline",
                    "Reactor modules: "
                            + reactor.size());

            final List<ModuleDependencyTree>
                    tgtTrees =
                    new DependencyAnalyzer("target",
                            ws.getTarget().getPath(),
                            reactor,
                            diagnostics).analyze();

            final List<DependencyChange> changes =
                    new DependencyDiffEngine()
                            .diff(baseTrees, tgtTrees);
            diagnostics.info("pipeline",
                    "Dependency changes: "
                            + changes.size());

            final List<ChangePoint> points =
                    computeChangePoints(changes);
            diagnostics.info("pipeline",
                    "Change points: "
                            + points.size());

            final ImpactResult impact =
                    computeImpact(points, tgtBuild);

            new ReportGenerator().generate(
                    changes, points, impact,
                    diagnostics.getEvents(),
                    format, output.toPath());
            diagnostics.info("pipeline",
                    "Report written to: "
                            + output);
        } finally {
            diagnostics.endStage("pipeline");
        }
    }

    /**
     * Extracts reactor module coordinates
     * from module dependency trees.
     *
     * @param trees module dep trees
     * @return set of reactor coords
     */
    private Set<ArtifactCoord> extractReactor(
            final List<ModuleDependencyTree>
                    trees) {
        final Set<ArtifactCoord> reactor =
                new HashSet<>();
        for (ModuleDependencyTree t : trees) {
            reactor.add(t.getModule());
        }
        return reactor;
    }

    /**
     * Computes change points for
     * version-changed dependencies.
     *
     * @param changes dependency changes
     * @return list of change points
     * @throws Exception if diff fails
     */
    private List<ChangePoint>
            computeChangePoints(
                    final List<DependencyChange>
                            changes)
            throws Exception {
        final List<DependencyChange> vc =
                new ArrayList<>();
        for (DependencyChange ch : changes) {
            if (ch.getChangeType()
                    == ChangeType.VERSION_CHANGED) {
                vc.add(ch);
            }
        }
        if (vc.isEmpty()) {
            diagnostics.info("pipeline",
                    "No version changes, "
                            + "skipping bytecode diff");
            return Collections.emptyList();
        }
        final List<JarLocationResult> jars =
                new JarLocator().locate(vc);
        final List<ChangePoint> pts =
                new ArrayList<>();
        final BytecodeDiffEngine engine =
                new BytecodeDiffEngine();
        for (JarLocationResult jar : jars) {
            pts.addAll(engine.diff(jar));
        }
        return pts;
    }

    /**
     * Computes impact result from
     * change points and call graph.
     *
     * @param points  change points
     * @param tgtBuild target build result
     * @return impact result
     */
    private ImpactResult computeImpact(
            final List<ChangePoint> points,
            final BuildResult tgtBuild) {
        if (points.isEmpty()) {
            diagnostics.info("pipeline",
                    "No change points, "
                            + "skipping call graph");
            return new ImpactResult(
                    Collections.emptyList(),
                    new EnumMap<>(
                            NotReportedReason
                                    .class));
        }
        final CallGraph cg =
                new CallGraphEngine(diagnostics)
                        .build(tgtBuild);
        return new ImpactTracer(diagnostics)
                .trace(points, cg, tgtBuild);
    }

    /**
     * Returns the diagnostics collector.
     *
     * @return collector
     */
    DiagnosticCollector getDiagnostics() {
        return diagnostics;
    }

    /**
     * Creates a configured CommandLine for the
     * given CLI instance.
     *
     * @param cli the CLI instance
     * @return configured CommandLine
     */
    static CommandLine newCommandLine(
            final ChangeImpactAnalyzeCli cli) {
        final CommandLine cmd =
                new CommandLine(cli);
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        return cmd;
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        final int exitCode = newCommandLine(
                new ChangeImpactAnalyzeCli())
                .execute(args);
        System.exit(exitCode);
    }
}
