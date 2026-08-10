package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.callgraph
        .CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph
        .EntrypointSelection;
import io.github.dependencyanalysis.callgraph
        .WalaReflectionOptions;
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

    /** Concurrent safe analysis tasks. */
    @Option(names = "--analysis-parallelism",
            defaultValue = "2",
            description = "Maximum concurrent Module, JAR diff, and "
                    + "decompilation tasks.")
    private int analysisParallelism;

    /** Command-wide Call Graph algorithm. */
    @Option(names = "--call-graph-algorithm",
            defaultValue = "rta",
            converter = CallGraphAlgorithmConverter.class,
            description = "Call Graph algorithm: rta, zero-cfa, "
                    + "optimized-0-1-cfa, or 1-object-1-call-site; "
                    + "default: rta.")
    private CallGraphAlgorithm callGraphAlgorithm;

    /** Command-wide WALA ReflectionOptions. */
    @Option(names = {"--wala-reflection-options", "--reflection-options"},
            defaultValue = "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD",
            converter = WalaReflectionOptionsConverter.class,
            description = "WALA ReflectionOptions enum name; default: "
                    + "ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD.")
    private WalaReflectionOptions reflectionOptions;

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
        if (analysisParallelism < 1) {
            diagnostics.error("preflight",
                    "--analysis-parallelism must be >= 1");
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
                        : normalizedDiagnostics));
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
            final MavenRuntimeDescriptor mavenRuntime = context.get(
                    ImpactPreflightService.MAVEN_RUNTIME,
                    MavenRuntimeDescriptor.class);
            final JavaRuntimeDescriptor targetJava = context.get(
                    ImpactPreflightService.JAVA_RUNTIME,
                    JavaRuntimeDescriptor.class);
            final MavenDependencyPluginRuntime pluginRuntime = context.get(
                    ImpactPreflightService.DEPENDENCY_PLUGIN_RUNTIME,
                    MavenDependencyPluginRuntime.class);
            final AnalysisRunResult result = new PerModuleImpactPipeline(
                    diagnostics, kinds,
                    mavenRuntime,
                    pluginRuntime,
                    context.get(
                            ImpactPreflightService
                                    .MAVEN_ARGS,
                            List.class),
                    targetJava,
                    new PerModulePipelineOptions(
                            callGraphTimeoutSeconds,
                            analysisParallelism,
                            new PipelineOutputPaths(context.get(
                                    ImpactPreflightService.COMMAND_RUN,
                                    CommandRunDirectory.class)
                                    .getTemporaryDirectory(),
                                    normalizedDiagnostics),
                            entrypointSelection,
                            callGraphAlgorithm,
                            reflectionOptions,
                            metrics.executors())).run(
                    context.get(
                            ImpactPreflightService
                                    .WORKSPACE,
                            WorkspaceResult.class));
            final String reportPath = output.toPath().toAbsolutePath()
                    .normalize().toString();
            final DiagnosticContext reportContext = DiagnosticContext.of(
                    "report", "publish");
            diagnostics.startStage(reportContext,
                    "Task started; path=" + reportPath);
            try {
                new PerModuleHtmlReportGenerator().generate(
                        result, diagnostics.getEvents(), report,
                        mavenRuntime, pluginRuntime,
                        targetJava, output.toPath());
                diagnostics.endStage(reportContext,
                        "Task completed; path=" + reportPath);
            } catch (Exception exception) {
                diagnostics.failStage(reportContext,
                        "Report publish failed: " + exception.getMessage()
                                + "; path=" + reportPath);
                throw exception;
            }
            emitSummary(diagnostics, result);
            return successful(result.getStatus()) ? 0 : 2;
        } catch (EntrypointSelectionException exception) {
            diagnostics.error("entrypoint-selection", exception.getMessage());
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

    private void emitSummary(
            final DiagnosticLog diagnostics,
            final AnalysisRunResult result) {
        final DiagnosticContext context = DiagnosticContext.of(
                "summary", "result");
        final String details = "; status=" + result.getStatus()
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
}
