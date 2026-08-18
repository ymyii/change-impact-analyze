package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphPolicy;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.dependency
        .DependencyArtifactSelection;
import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticContext;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticLog;
import io.github.dependencyanalysis.metrics
        .RuntimeMetricsSession;
import io.github.dependencyanalysis.preflight
        .PreflightConsoleRenderer;
import io.github.dependencyanalysis.preflight
        .PreflightContext;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.report
        .PerModuleHtmlReportGenerator;
import io.github.dependencyanalysis.runtime
        .CommandRunDirectory;
import io.github.dependencyanalysis.runtime
        .JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime
        .ReportCache;
import io.github.dependencyanalysis.workspace
        .WorkspaceResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/** Existing change impact analysis subcommand. */
@Command(
        name = "impact",
        mixinStandardHelpOptions = true,
        description = "Analyze impact of dependency upgrades."
)
public final class ImpactCommand
        implements Callable<Integer> {

    /**
     * Computes the runtime default for bounded parallel stages.
     *
     * @param availableProcessors processors visible to the Analyzer JVM
     * @return half the processors, with a minimum of one
     */
    static int defaultAnalysisParallelism(final int availableProcessors) {
        return Math.max(1, availableProcessors / 2);
    }

    /** Root options. */
    @ParentCommand
    private DependencyAnalyzerCli root;

    /** Analysis path. */
    @Option(names = {"-p", "--path"},
            description = "Git repository analysis directory."
                    + " Defaults to current directory.")
    private File path;

    /** Baseline local ref. */
    @Option(names = {"-b", "--baseline"},
            required = true,
            description = "Baseline local Git ref.")
    private String baseline;

    /** Target local ref. */
    @Option(names = {"-t", "--target"},
            description = "Target local Git ref."
                    + " Defaults to current checkout.")
    private String target;

    /** Output report file. */
    @Option(names = {"-o", "--output"},
            required = true,
            description = "Output report file.")
    private File output;

    /** Optional read-only Call Graph benchmark diagnostics JSON. */
    @Option(names = "--call-graph-diagnostics-output",
            description = "Optional JSON path for read-only Call Graph "
                    + "benchmark CGNode topology, source, and IR.")
    private File callGraphDiagnosticsOutput;

    /** Report format. */
    @Option(names = {"-f", "--format"},
            defaultValue = "HTML",
            description = "Output format: HTML only; MD is rejected.")
    private OutputFormat format;

    /** Analysis target profile. */
    @Option(names = "--analysis-target",
            defaultValue = "spring-backend",
            description = "Analysis target: spring-backend.")
    private String analysisTarget;

    /** Global Analyzer concurrency limit. */
    @Option(names = "--analysis-parallelism",
            description = "Maximum concurrent Analyzer operations; default: "
                    + "half of available processors.")
    private Integer analysisParallelism;

    /** Command-wide Call Graph algorithm. */
    @Option(names = "--call-graph-algorithm",
            defaultValue = "cha",
            converter = CallGraphAlgorithmConverter.class,
            description = "Call Graph algorithm: cha or k-obj "
                    + "(experimental); default: cha.")
    private CallGraphAlgorithm callGraphAlgorithm;

    /** Optional command-wide k-object receiver allocation-string depth. */
    @Option(names = "--k-obj-depth",
            description = "Positive receiver allocation-string depth for "
                    + "k-obj; default: 1.")
    private Integer kObjDepth;

    /** Command-wide JDK Method Model selection. */
    @Option(names = "--jdk-model",
            converter = JdkModelSelectionConverter.class,
            description = "JDK Method Model: jdk8 or none; "
                    + "default: none for cha, jdk8 for k-obj.")
    private JdkModelSelection jdkModel;

    /** Command-wide WALA ReflectionOptions. */
    @Option(names = {"--wala-reflection-options", "--reflection-options"},
            defaultValue = "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD",
            converter = WalaReflectionOptionsConverter.class,
            description = "WALA ReflectionOptions enum name; default: "
                    + "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD.")
    private WalaReflectionOptions reflectionOptions;

    /** External dependency method-body analysis scope. */
    @Option(names = "--dependency-analysis-scope",
            defaultValue = "changed-paths",
            converter = DependencyAnalysisScopeModeConverter.class,
            description = "Dependency method-body scope: changed-paths "
                    + "or full; default: changed-paths.")
    private DependencyAnalysisScopeMode dependencyAnalysisScope;

    /** Included PROJECT entrypoint classes. */
    @Option(names = "--entrypoint-include",
            description = "Repeatable slash-separated class-path pattern "
                    + "PROJECT entrypoint include.")
    private List<String> entrypointIncludes = new java.util.ArrayList<>();

    /** Excluded PROJECT entrypoint classes. */
    @Option(names = "--entrypoint-exclude",
            description = "Repeatable slash-separated class-path pattern "
                    + "PROJECT entrypoint exclude.")
    private List<String> entrypointExcludes = new java.util.ArrayList<>();

    /** Included changed Maven dependency JAR sources. */
    @Option(names = "--dependency-include",
            description = "Repeatable changed dependency include Glob: "
                    + "groupPattern:artifactPattern.")
    private List<String> dependencyIncludes = new java.util.ArrayList<>();

    /** Excluded changed Maven dependency JAR sources. */
    @Option(names = "--dependency-exclude",
            description = "Repeatable changed dependency exclude Glob: "
                    + "groupPattern:artifactPattern; exclude wins.")
    private List<String> dependencyExcludes = new java.util.ArrayList<>();

    /** Included change point kinds. */
    @Option(names = {"-k", "--include-change-kinds"},
            split = ",",
            description = "Included ChangePointKind values.")
    private Set<ChangePointKind> kinds =
            new HashSet<>(ChangePointKind
                    .DEFAULT_INCLUDED_KINDS);

    /** Optional Call Graph timeout. */
    @Option(names = "--call-graph-timeout-seconds",
            defaultValue = "0",
            description = "Per-Module WALA Call Graph timeout in seconds;"
                    + " 0 means unlimited.")
    private long callGraphTimeoutSeconds;

    @Override
    public Integer call() {
        final DiagnosticLog diagnostics = new DiagnosticLog(
                System.err, root.getLogVerbosity());
        try (RuntimeMetricsSession metrics =
                     RuntimeMetricsSession.start(diagnostics)) {
            return execute(diagnostics, metrics);
        }
    }

    private Integer execute(
            final DiagnosticLog diagnostics,
            final RuntimeMetricsSession metrics) {
        diagnostics.debug("cli",
                "command=impact; verbosity="
                        + root.getLogVerbosity());
        if (callGraphTimeoutSeconds < 0) {
            diagnostics.error("preflight",
                    "--call-graph-timeout-seconds must be >= 0");
            return 1;
        }
        final int selectedAnalysisParallelism = analysisParallelism == null
                ? defaultAnalysisParallelism(
                Runtime.getRuntime().availableProcessors())
                : analysisParallelism;
        if (selectedAnalysisParallelism < 1) {
            diagnostics.error("preflight",
                    "--analysis-parallelism must be >= 1");
            return 1;
        }
        if (analysisParallelism != null && selectedAnalysisParallelism
                > Runtime.getRuntime().availableProcessors()) {
            diagnostics.warn("preflight",
                    "--analysis-parallelism exceeds availableProcessors: "
                            + selectedAnalysisParallelism);
        }
        analysisParallelism = selectedAnalysisParallelism;
        final Integer selectedKObjDepth = selectedKObjDepth(diagnostics);
        if (selectedKObjDepth == null) {
            return 1;
        }
        if (!resolveCallGraphPolicy(diagnostics)) {
            return 1;
        }
        final EntrypointSelection entrypointSelection;
        try {
            entrypointSelection = EntrypointSelection.parse(
                    entrypointIncludes, entrypointExcludes);
        } catch (IllegalArgumentException exception) {
            diagnostics.error("preflight", exception.getMessage());
            return 1;
        }
        final DependencyArtifactSelection dependencySelection;
        try {
            dependencySelection = DependencyArtifactSelection.parse(
                    dependencyIncludes, dependencyExcludes);
        } catch (IllegalArgumentException exception) {
            diagnostics.error("preflight", exception.getMessage());
            return 1;
        }
        if (!"spring-backend".equalsIgnoreCase(analysisTarget)) {
            diagnostics.error("preflight",
                    "--analysis-target currently accepts only spring-backend");
            return 1;
        }
        if (format != OutputFormat.HTML) {
            diagnostics.error("preflight",
                    "Markdown output was removed; --format accepts only HTML");
            return 1;
        }
        if (path == null) {
            path = new File(System.getProperty(
                    "user.dir"));
        }
        final java.nio.file.Path normalizedReport = output.toPath()
                .toAbsolutePath().normalize();
        final java.nio.file.Path normalizedDiagnostics =
                callGraphDiagnosticsOutput == null ? null
                        : callGraphDiagnosticsOutput.toPath()
                        .toAbsolutePath().normalize();
        if (normalizedReport.equals(normalizedDiagnostics)) {
            diagnostics.error("preflight",
                    "--call-graph-diagnostics-output must differ from "
                            + "--output");
            return 1;
        }
        diagnostics.trace("cli",
                "path=" + path.toPath().toAbsolutePath().normalize()
                        + "; baseline=" + baseline
                        + "; target="
                        + (target == null ? "CURRENT" : target)
                        + "; output=" + output.toPath()
                                .toAbsolutePath().normalize()
                        + "; callGraphDiagnosticsOutput="
                        + (normalizedDiagnostics == null ? "DISABLED"
                        : normalizedDiagnostics)
                        + (callGraphAlgorithm == CallGraphAlgorithm.K_OBJ
                        ? "; experimental=true; kObjDepth="
                        + selectedKObjDepth : "")
                        + "; jdkModel=" + jdkModel.identifier()
                        + "; methodBodyEquivalence="
                        + "decompiled-java-first-short-circuit"
                        + "; dependencySelection=" + dependencySelection);
        try (PreflightContext context =
                     new PreflightContext()) {
            final PreflightReport report =
                    new ImpactPreflightService(
                            root, path, baseline,
                            target, output,
                            diagnostics).run(context);
            new PreflightConsoleRenderer().render(
                    report, diagnostics);
            if (report.blocksCommand()) {
                return 1;
            }
            return analyzeAndReport(context, report, diagnostics, metrics,
                    normalizedDiagnostics, new AnalysisSelections(
                            entrypointSelection, selectedKObjDepth,
                            dependencySelection));
        } catch (EntrypointSelectionException exception) {
            diagnostics.error("entrypoint-selection", exception.getMessage());
            return 1;
        } catch (DependencySelectionException exception) {
            diagnostics.error("dependency-selection", exception.getMessage());
            return 1;
        } catch (Exception exception) {
            diagnostics.error("pipeline",
                    "Pipeline failed: "
                            + exception.getMessage());
            diagnostics.transientException(
                    DiagnosticContext.stage("pipeline"), exception);
            return 2;
        }
    }

    private int analyzeAndReport(
            final PreflightContext context,
            final PreflightReport report,
            final DiagnosticLog diagnostics,
            final RuntimeMetricsSession metrics,
            final java.nio.file.Path normalizedDiagnostics,
            final AnalysisSelections selections)
            throws Exception {
        final MavenRuntimeDescriptor mavenRuntime = context.get(
                ImpactPreflightService.MAVEN_RUNTIME,
                MavenRuntimeDescriptor.class);
        final JavaRuntimeDescriptor targetJava = context.get(
                ImpactPreflightService.JAVA_RUNTIME,
                JavaRuntimeDescriptor.class);
        final MavenDependencyPluginRuntime pluginRuntime = context.get(
                ImpactPreflightService.DEPENDENCY_PLUGIN_RUNTIME,
                MavenDependencyPluginRuntime.class);
        final CommandRunDirectory commandRun = context.get(
                ImpactPreflightService.COMMAND_RUN,
                CommandRunDirectory.class);
        final AnalysisRunResult result;
        try (ReportCache reportCache = new ReportCache(
                commandRun, "impact")) {
            final ImpactExecutionEngine engine = new PerModuleImpactPipeline(
                    diagnostics, kinds, mavenRuntime, pluginRuntime,
                    context.get(ImpactPreflightService.MAVEN_ARGS, List.class),
                    targetJava, new PerModulePipelineOptions(
                    callGraphTimeoutSeconds, analysisParallelism,
                    new PipelineOutputPaths(commandRun.getTemporaryDirectory(),
                            normalizedDiagnostics), selections.entrypoints(),
                    callGraphAlgorithm, selections.kObjDepth(),
                    reflectionOptions,
                    dependencyAnalysisScope, jdkModel,
                    selections.dependencies(),
                    metrics.executors(), reportCache));
            result = engine.run(context.get(
                    ImpactPreflightService.WORKSPACE, WorkspaceResult.class));
            reportCache.complete();
            publishReport(result, report, diagnostics, mavenRuntime,
                    pluginRuntime, targetJava);
        }
        emitSummary(diagnostics, result);
        return successful(result.getStatus()) ? 0 : 2;
    }

    private void publishReport(
            final AnalysisRunResult result,
            final PreflightReport report,
            final DiagnosticLog diagnostics,
            final MavenRuntimeDescriptor mavenRuntime,
            final MavenDependencyPluginRuntime pluginRuntime,
            final JavaRuntimeDescriptor targetJava) {
        final String reportPath = output.toPath().toAbsolutePath()
                .normalize().toString();
        final DiagnosticContext reportContext = DiagnosticContext.of(
                "report", "publish");
        diagnostics.startStage(reportContext,
                "path=" + reportPath);
        try {
            new PerModuleHtmlReportGenerator(diagnostics).generate(
                    result, report, mavenRuntime, pluginRuntime,
                    targetJava, output.toPath());
            diagnostics.endStage(reportContext,
                    "path=" + reportPath);
        } catch (RuntimeException exception) {
            diagnostics.failStage(reportContext,
                    "reason=" + exception.getMessage()
                            + "; path=" + reportPath);
            throw exception;
        }
    }

    private boolean resolveCallGraphPolicy(
            final DiagnosticLog diagnostics) {
        if (jdkModel == null) {
            jdkModel = CallGraphPolicy.defaultJdkModel(callGraphAlgorithm);
        }
        try {
            CallGraphPolicy.validate(callGraphAlgorithm, jdkModel);
            return true;
        } catch (IllegalArgumentException exception) {
            diagnostics.error("preflight", exception.getMessage());
            return false;
        }
    }

    private Integer selectedKObjDepth(final DiagnosticLog diagnostics) {
        if (kObjDepth != null
                && callGraphAlgorithm != CallGraphAlgorithm.K_OBJ) {
            diagnostics.error("preflight", "--k-obj-depth is valid only "
                    + "with --call-graph-algorithm k-obj");
            return null;
        }
        final int selected = kObjDepth == null
                ? CallGraphAlgorithm.defaultKObjDepth() : kObjDepth;
        try {
            return CallGraphAlgorithm.requireValidKObjDepth(selected);
        } catch (IllegalArgumentException exception) {
            diagnostics.error("preflight", exception.getMessage());
            return null;
        }
    }

    private void emitSummary(
            final DiagnosticLog diagnostics,
            final AnalysisRunResult result) {
        final DiagnosticContext context = DiagnosticContext.of(
                "summary", "result");
        final long uniqueImpactPaths = result.getModuleResults().stream()
                .flatMap(module -> module.getImpactPaths().stream())
                .map(impactPath -> impactPath.getNodes().stream()
                        .map(node -> node.methodId().toString())
                        .toList().toString())
                .distinct().count();
        final long rootMethods = result.getModuleResults().stream()
                .flatMap(module -> module.getImpactPaths().stream())
                .map(ImpactPath::getRootMethod).distinct().count();
        final long affectedMethods = result.getModuleResults().stream()
                .flatMap(module -> module.getImpactPaths().stream())
                .flatMap(impactPath -> impactPath.getAffectedMethods()
                        .stream())
                .distinct().count();
        final long changedMembers = result.getModuleResults().stream()
                .flatMap(module -> java.util.stream.Stream.concat(
                        module.getImpactPaths().stream().map(impactPath ->
                                impactPath.getTerminal().getChangePoint()),
                        module.getStructuralPaths().stream().map(
                                StructuralReferencePath::getChangePoint)))
                .distinct().count();
        final String details = "; status=" + result.getStatus()
                + "; uniqueImpactPaths=" + uniqueImpactPaths
                + "; rootMethods=" + rootMethods
                + "; affectedMethods=" + affectedMethods
                + "; changedMembers=" + changedMembers
                + "; path=" + output.toPath().toAbsolutePath()
                .normalize();
        switch (result.getStatus()) {
            case SUCCESS -> diagnostics.info(context,
                    "Impact analysis completed" + details);
            case INCONCLUSIVE, PARTIAL_SUCCESS -> diagnostics.warn(context,
                    "Impact analysis completed with limitations" + details);
            case FAILED -> diagnostics.error(context,
                    "Impact analysis failed" + details);
            default -> throw new IllegalArgumentException(
                    "Unsupported analysis status: " + result.getStatus());
        }
    }

    private boolean successful(final AnalysisStatus status) {
        return status == AnalysisStatus.SUCCESS
                || status == AnalysisStatus.INCONCLUSIVE;
    }

    /**
     * Validated command selections passed into the analysis pipeline.
     *
     * @param entrypoints PROJECT entrypoint boundary
     * @param kObjDepth k-object allocation-string depth
     * @param dependencies changed dependency source boundary
     */
    private record AnalysisSelections(
            EntrypointSelection entrypoints,
            int kObjDepth,
            DependencyArtifactSelection dependencies) {
    }
}
