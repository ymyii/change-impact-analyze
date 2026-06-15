package io.github.changeimpact.analyze.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

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
public final class ChangeImpactAnalyzeCli implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        final int exitCode =
                new CommandLine(new ChangeImpactAnalyzeCli()).execute(args);
        System.exit(exitCode);
    }
}
