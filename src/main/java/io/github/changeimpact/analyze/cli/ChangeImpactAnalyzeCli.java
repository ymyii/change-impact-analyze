package io.github.changeimpact.analyze.cli;

import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
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
                    "Project path does not exist: "
                            + project);
            System.err.println(
                    "Project path does not exist: "
                            + project);
            return 1;
        }

        if (!project.isDirectory()) {
            diagnostics.failStage("validation",
                    "Project path is not a directory: "
                            + project);
            System.err.println(
                    "Project path is not a directory: "
                            + project);
            return 1;
        }

        if (baseline == null || baseline.isBlank()) {
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
        diagnostics.startStage("analysis");
        diagnostics.info("analysis",
                "Analysis flow not yet implemented");
        diagnostics.endStage("analysis");
        return 0;
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
