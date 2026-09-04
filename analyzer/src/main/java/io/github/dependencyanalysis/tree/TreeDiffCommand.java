package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.metrics.RuntimeMetricsSession;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

// Wiki: wiki/use-cases/dependency-analyzer-compare-dependency-trees.md - Entry
/** Compares two repository dependency tree snapshots. */
@Command(
        name = "diff",
        mixinStandardHelpOptions = true,
        description = "Compare baseline and target dependency trees."
)
public final class TreeDiffCommand implements Callable<Integer> {

    /** Tree parent. */
    @ParentCommand
    private TreeCommand parent;

    /** Shared tree options. */
    @Mixin
    private TreeCommonOptions common;

    /** Required baseline commit-ish. */
    @Option(names = {"-b", "--baseline"}, required = true,
            description = "Baseline local Git commit-ish.")
    private String baseline;

    /** Optional target commit-ish. */
    @Option(names = {"-t", "--target"},
            description = "Target local Git commit-ish."
                    + " Defaults to the current workspace.")
    private String target;

    @Override
    public Integer call() {
        final DiagnosticLog diagnostics = new DiagnosticLog(
                System.err, parent.root().getLogVerbosity());
        try (RuntimeMetricsSession ignored =
                     RuntimeMetricsSession.start(diagnostics)) {
            return new TreeDiffExecutionEngine(parent.root(), common,
                    baseline, target, diagnostics).execute();
        }
    }
}
