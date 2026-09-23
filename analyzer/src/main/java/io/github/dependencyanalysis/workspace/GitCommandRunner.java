package io.github.dependencyanalysis.workspace;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.util.CommandResolver;
import io.github.dependencyanalysis.util.ProcessConsoleExecutor;
import io.github.dependencyanalysis.util.ProcessConsoleResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Executes Git, keeping return data separate from streamed diagnostics. */
final class GitCommandRunner {

    /** Git working directory. */
    private final Path workDir;

    /** Console destination. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates a runner.
     * @param directory Git working directory
     * @param log Console destination
     */
    GitCommandRunner(final Path directory, final DiagnosticLog log) {
        workDir = directory;
        diagnostics = log;
    }

    /**
     * Runs Git with concurrent stream readers.
     * @param args Git arguments
     * @return exit status and command return data
     * @throws IOException on process or reader failure
     * @throws InterruptedException when interrupted
     */
    GitCommandResult run(final String... args)
            throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("core.longpaths=true");
        command.addAll(Arrays.asList(args));
        final ProcessBuilder builder = new ProcessBuilder(
                CommandResolver.resolve(command)).directory(workDir.toFile());
        final DiagnosticContext context = DiagnosticContext.of(
                "workspace", "git." + args[0]);
        final ProcessConsoleResult result = args[0].equals("worktree")
                ? ProcessConsoleExecutor.execute(builder, diagnostics, context)
                : ProcessConsoleExecutor.executeData(
                        builder, diagnostics, context);
        return GitCommandResult.of(result.exitCode(), result.standardOutput());
    }
}
