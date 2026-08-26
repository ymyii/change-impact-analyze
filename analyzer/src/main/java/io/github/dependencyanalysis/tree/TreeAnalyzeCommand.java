package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.metrics.RuntimeMetricsSession;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/** Generates one repository dependency tree report. */
@Command(
        name = "analyze",
        mixinStandardHelpOptions = true,
        description = "Generate a repository dependency tree HTML report."
)
public final class TreeAnalyzeCommand implements Callable<Integer> {

    /** Tree parent. */
    @ParentCommand
    private TreeCommand parent;

    /** Shared tree options. */
    @Mixin
    private TreeCommonOptions common;

    /** Local ref. */
    @Option(names = {"-r", "--ref"},
            description = "Local Git commit-ish."
                    + " Defaults to current checkout.")
    private String ref;

    @Override
    public Integer call() {
        final DiagnosticLog diagnostics = new DiagnosticLog(
                System.err, parent.root().getLogVerbosity());
        try (RuntimeMetricsSession ignored =
                     RuntimeMetricsSession.start(diagnostics)) {
            return new TreeExecutionEngine(parent.root(), common.path(),
                    ref, common.output(), common.scopes(),
                    common.pluginVersion(), diagnostics).execute();
        }
    }
}
