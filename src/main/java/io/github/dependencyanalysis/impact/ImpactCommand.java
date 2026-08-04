package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.callgraph
        .EntrypointSelection;
import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticCollector;
import io.github.dependencyanalysis.diagnostic
        .LogVerbosity;
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

    /** Included PROJECT entrypoint classes. */
    @Option(names = "--entrypoint-include",
            description = "Repeatable package-pattern:class-pattern "
                    + "PROJECT entrypoint include.")
    private List<String> entrypointIncludes = new java.util.ArrayList<>();

    /** Excluded PROJECT entrypoint classes. */
    @Option(names = "--entrypoint-exclude",
            description = "Repeatable package-pattern:class-pattern "
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

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics =
            new DiagnosticCollector();

    @Override
    public Integer call() {
        diagnostics.setVerbosity(
                root.getLogVerbosity());
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
        diagnostics.trace("cli",
                "path=" + path.toPath().toAbsolutePath().normalize()
                        + "; baseline=" + baseline
                        + "; target="
                        + (target == null ? "CURRENT" : target)
                        + "; output=" + output.toPath()
                                .toAbsolutePath().normalize());
        try (PreflightContext context =
                     new PreflightContext()) {
            final PreflightReport report =
                    new ImpactPreflightService(
                            root, path, baseline,
                            target, output,
                            diagnostics).run(context);
            new PreflightConsoleRenderer().render(
                    report, System.err);
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
                            context.get(
                                    ImpactPreflightService.COMMAND_RUN,
                                    CommandRunDirectory.class)
                                    .getTemporaryDirectory(),
                            entrypointSelection)).run(
                    context.get(
                            ImpactPreflightService
                                    .WORKSPACE,
                            WorkspaceResult.class));
            new PerModuleHtmlReportGenerator().generate(
                    result, diagnostics.getEvents(), report,
                    mavenRuntime, pluginRuntime,
                    targetJava, output.toPath());
            return result.getStatus() == AnalysisStatus.SUCCESS
                    || result.getStatus() == AnalysisStatus.INCONCLUSIVE
                    ? 0 : 2;
        } catch (EntrypointSelectionException exception) {
            diagnostics.error("entrypoint-selection", exception.getMessage());
            return 1;
        } catch (Exception exception) {
            diagnostics.error("pipeline",
                    "Pipeline failed: "
                            + exception.getMessage());
            if (root.getLogVerbosity().includes(
                    LogVerbosity.DEBUG)) {
                exception.printStackTrace(System.err);
            }
            return 2;
        }
    }
}
