package io.github.dependencyanalysis.util;

import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

/** Executes a process and streams its combined output to the Console. */
public final class ProcessConsoleExecutor {

    /** Maven error prefix. */
    private static final String ERROR = "[ERROR]";

    /** Maven warning prefixes. */
    private static final String WARNING = "[WARNING]";

    /** Alternate Maven warning prefix. */
    private static final String WARN = "[WARN]";

    /** Private constructor. */
    private ProcessConsoleExecutor() {
    }

    /**
     * Executes a configured process.
     *
     * <p>Errors and warnings are visible at default verbosity. All other
     * process lines are visible with {@code -v}. Output is retained only as
     * a bounded in-memory failure tail.</p>
     *
     * @param builder configured process builder
     * @param diagnostics Console diagnostics
     * @param context process context
     * @param tailLimit maximum retained tail lines
     * @return exit code and output tail
     * @throws IOException when the process or output reader fails
     * @throws InterruptedException when execution is interrupted
     */
    public static ProcessConsoleResult execute(
            final ProcessBuilder builder,
            final DiagnosticCollector diagnostics,
            final DiagnosticContext context,
            final int tailLimit)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        final Deque<String> tail = new ArrayDeque<>();
        final AtomicReference<IOException> outputFailure =
                new AtomicReference<>();
        final Thread pump = new Thread(
                () -> pump(process, diagnostics, context,
                        tailLimit, tail, outputFailure),
                "dependency-analyzer-process-output");
        pump.setDaemon(true);
        pump.start();
        try {
            final int exitCode = process.waitFor();
            pump.join();
            if (outputFailure.get() != null) {
                throw outputFailure.get();
            }
            return new ProcessConsoleResult(
                    exitCode, String.join("\n", tail));
        } catch (InterruptedException exception) {
            ProcessTreeTerminator.terminate(process);
            pump.interrupt();
            throw exception;
        }
    }

    private static void pump(
            final Process process,
            final DiagnosticCollector diagnostics,
            final DiagnosticContext context,
            final int tailLimit,
            final Deque<String> tail,
            final AtomicReference<IOException> failure) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                retain(tail, line, tailLimit);
                if (!line.isBlank()) {
                    diagnostics.console(context,
                            level(line), line);
                }
            }
        } catch (IOException exception) {
            failure.set(exception);
        }
    }

    private static void retain(
            final Deque<String> tail,
            final String line,
            final int limit) {
        if (limit <= 0) {
            return;
        }
        while (tail.size() >= limit) {
            tail.removeFirst();
        }
        tail.addLast(line);
    }

    private static DiagnosticLevel level(final String line) {
        if (line.contains(ERROR)) {
            return DiagnosticLevel.ERROR;
        }
        if (line.contains(WARNING) || line.contains(WARN)) {
            return DiagnosticLevel.WARN;
        }
        return DiagnosticLevel.DEBUG;
    }
}
