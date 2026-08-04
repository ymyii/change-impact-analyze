package io.github.dependencyanalysis.runtime;

import io.github.dependencyanalysis.util.CommandResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Maven without shell token interpolation. */
public final class MavenExecutor {

    /**
     * Runs Maven and captures UTF-8 output.
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
        final List<String> command =
                new ArrayList<>();
        command.add(runtime.getExecutable()
                .toString());
        command.addAll(arguments);
        final ProcessBuilder builder =
                new ProcessBuilder(
                        CommandResolver.resolve(
                                command));
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        final Path javaHome = runtime.getJavaHome();
        if (javaHome != null) {
            builder.environment().put(
                    "JAVA_HOME",
                    javaHome.toString());
        }
        final Process process = builder.start();
        final String stdout = new String(
                process.getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        final int exitCode = process.waitFor();
        return new MavenExecutionResult(
                exitCode, stdout, "");
    }
}
