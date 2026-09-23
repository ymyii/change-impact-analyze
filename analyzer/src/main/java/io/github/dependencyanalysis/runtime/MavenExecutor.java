package io.github.dependencyanalysis.runtime;

import io.github.dependencyanalysis.cli.MavenArguments;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.util.CommandResolver;
import io.github.dependencyanalysis.util.ProcessConsoleExecutor;
import io.github.dependencyanalysis.util.ProcessConsoleResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Maven without shell token interpolation. */
public final class MavenExecutor {

    /**
     * Runs Maven with the default Console destination.
     *
     * @param runtime selected runtime
     * @param workDir working directory
     * @param arguments process tokens
     * @return process result
     * @throws IOException when process cannot start
     * @throws InterruptedException when interrupted
     */
    public MavenExecutionResult execute(
            final MavenRuntimeDescriptor runtime,
            final Path workDir,
            final List<String> arguments)
            throws IOException,
            InterruptedException {
        return execute(runtime, workDir, arguments, new DiagnosticLog(),
                DiagnosticContext.of("maven", "execute"));
    }

    /**
     * Streams Maven logs, retaining stdout only for a version probe.
     * @param runtime selected runtime
     * @param workDir working directory
     * @param arguments Maven arguments
     * @param diagnostics Console destination
     * @param context operation source
     * @return exit status and optional version data
     * @throws IOException on process or reader failure
     * @throws InterruptedException when interrupted
     */
    public MavenExecutionResult execute(
            final MavenRuntimeDescriptor runtime,
            final Path workDir,
            final List<String> arguments,
            final DiagnosticLog diagnostics,
            final DiagnosticContext context)
            throws IOException, InterruptedException {
        final ProcessBuilder builder = processBuilder(runtime, workDir,
                MavenArguments.withVerbosity(
                        arguments, diagnostics.getVerbosity()));
        final ProcessConsoleResult result = arguments.contains("--version")
                ? ProcessConsoleExecutor.executeData(
                        builder, diagnostics, context)
                : ProcessConsoleExecutor.execute(builder, diagnostics, context);
        return new MavenExecutionResult(
                result.exitCode(), result.standardOutput());
    }

    private ProcessBuilder processBuilder(
            final MavenRuntimeDescriptor runtime,
            final Path workDir,
            final List<String> arguments) {
        final List<String> command = new ArrayList<>();
        command.add(runtime.getExecutable().toString());
        command.addAll(arguments);
        final ProcessBuilder builder = new ProcessBuilder(
                CommandResolver.resolve(command));
        builder.directory(workDir.toFile());
        final Path javaHome = runtime.getJavaHome();
        if (javaHome != null) {
            builder.environment().put("JAVA_HOME", javaHome.toString());
        }
        return builder;
    }
}
