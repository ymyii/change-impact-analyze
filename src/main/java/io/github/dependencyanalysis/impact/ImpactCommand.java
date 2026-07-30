package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode
        .ChangePointKind;
import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticCollector;
import io.github.dependencyanalysis.preflight
        .PreflightConsoleRenderer;
import io.github.dependencyanalysis.preflight
        .PreflightContext;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .CommandRunDirectory;
import io.github.dependencyanalysis.runtime
        .JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
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
            description = "Output format: html or md.")
    private OutputFormat format;

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
            description = "WALA RTA timeout in seconds;"
                    + " 0 means unlimited.")
    private long callGraphTimeoutSeconds;

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics =
            new DiagnosticCollector();

    @Override
    public Integer call() {
        if (callGraphTimeoutSeconds < 0) {
            diagnostics.error("preflight",
                    "--call-graph-timeout-seconds must be >= 0");
            return 1;
        }
        if (path == null) {
            path = new File(System.getProperty(
                    "user.dir"));
        }
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
            new ImpactPipeline(
                    diagnostics, kinds,
                    context.get(
                            ImpactPreflightService
                                    .MAVEN_RUNTIME,
                            MavenRuntimeDescriptor.class),
                    context.get(
                            ImpactPreflightService
                                    .MAVEN_ARGS,
                            List.class),
                    context.get(
                            ImpactPreflightService
                                    .JAVA_RUNTIME,
                            JavaRuntimeDescriptor.class),
                    callGraphTimeoutSeconds,
                    context.get(
                            ImpactPreflightService
                                    .COMMAND_RUN,
                            CommandRunDirectory.class)
                            .getTemporaryDirectory()).run(
                    context.get(
                            ImpactPreflightService
                                    .WORKSPACE,
                            WorkspaceResult.class),
                    output, format, report);
            return 0;
        } catch (Exception exception) {
            diagnostics.error("pipeline",
                    "Pipeline failed: "
                            + exception.getMessage());
            exception.printStackTrace(System.err);
            return 2;
        }
    }
}
