package io.github.changeimpact.analyze.workspace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes git commands via ProcessBuilder
 * and captures output.
 */
final class GitCommandRunner {

    /** Working directory for git commands. */
    private final Path workDir;

    /**
     * Creates a runner bound to a directory.
     *
     * @param directory working directory
     */
    GitCommandRunner(final Path directory) {
        this.workDir = directory;
    }

    /**
     * Runs a git command with the given args.
     *
     * @param args git sub-command and args
     * @return command result
     * @throws IOException if process fails
     * @throws InterruptedException if interrupted
     */
    GitCommandResult run(
            final String... args)
            throws IOException,
            InterruptedException {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));
        final ProcessBuilder pb =
                new ProcessBuilder(cmd)
                        .directory(
                                workDir.toFile())
                        .redirectErrorStream(
                                false);
        final Process proc = pb.start();
        final String out = readStream(
                proc.getInputStream());
        final String err = readStream(
                proc.getErrorStream());
        final int code = proc.waitFor();
        return GitCommandResult.of(
                code, out, err);
    }

    private String readStream(
            final InputStream is)
            throws IOException {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                is,
                                StandardCharsets
                                        .UTF_8))) {
            return reader.lines()
                    .collect(Collectors.joining(
                            "\n"));
        }
    }
}
