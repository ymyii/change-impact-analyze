package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticLog;
import io.github.dependencyanalysis.metrics
        .RuntimeMetricsSession;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.concurrent.Callable;

// Wiki: wiki/features/repository-dependency-tree-report.md - tree command 入口
/** Repository dependency tree report command. */
@Command(
        name = "tree",
        mixinStandardHelpOptions = true,
        description = "Generate repository dependency tree HTML."
)
public final class TreeCommand
        implements Callable<Integer> {

    /** Root options. */
    @ParentCommand
    private DependencyAnalyzerCli root;

    /** Analysis path inside a Git repository. */
    @Option(names = {"-p", "--path"},
            description = "Analysis path inside a Git repository."
                    + " Defaults to current directory.")
    private File path;

    /** Local ref. */
    @Option(names = {"-r", "--ref"},
            description = "Local Git ref."
                    + " Defaults to current checkout.")
    private String ref;

    /** Output directory. */
    @Option(names = {"-o", "--output"}, required = true,
            description = "HTML report output directory.")
    private File output;

    /** Scope CSV. */
    @Option(names = {"-s", "--scopes"},
            defaultValue = "compile,runtime,provided,system",
            description = "Included dependency scopes.")
    private String scopes;

    /** Dependency plugin override. */
    @Option(names = {"-d", "--dependency-plugin-version"},
            description = "Optional maven-dependency-plugin version.")
    private String pluginVersion;

    @Override
    public Integer call() {
        final DiagnosticLog diagnostics = new DiagnosticLog(
                System.err, root.getLogVerbosity());
        try (RuntimeMetricsSession ignored =
                     RuntimeMetricsSession.start(diagnostics)) {
            return execute(diagnostics);
        }
    }

    private Integer execute(final DiagnosticLog diagnostics) {
        return new TreeExecutionEngine(root, path, ref, output,
                scopes, pluginVersion, diagnostics).execute();
    }
}
