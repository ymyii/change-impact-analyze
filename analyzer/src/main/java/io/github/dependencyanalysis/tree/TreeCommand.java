package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli.DependencyAnalyzerCli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.Callable;

// Wiki: wiki/features/repository-dependency-tree-report.md - tree command 入口
// Wiki: wiki/features/repository-dependency-tree-diff.md - tree diff分派入口
/** Repository dependency tree report command. */
@Command(
        name = "tree",
        mixinStandardHelpOptions = true,
        description = "Analyze or compare repository dependency trees.",
        subcommands = {
            TreeAnalyzeCommand.class,
            TreeDiffCommand.class
        }
)
public final class TreeCommand
        implements Callable<Integer> {

    /** Root options. */
    @ParentCommand
    private DependencyAnalyzerCli root;

    /** Picocli command model. */
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return 1;
    }

    /** @return root command options */
    DependencyAnalyzerCli root() {
        return root;
    }
}
