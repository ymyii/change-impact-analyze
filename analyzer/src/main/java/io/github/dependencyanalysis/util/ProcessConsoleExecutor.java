package io.github.dependencyanalysis.util;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

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

    /** Maven informational prefix. */
    private static final String INFO = "[INFO]";

    /** Maven debug prefix. */
    private static final String DEBUG = "[DEBUG]";

    /** Maven trace prefix. */
    private static final String TRACE = "[TRACE]";

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
            final DiagnosticLog diagnostics,
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
            final DiagnosticLog diagnostics,
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
                    final OutputClassification classification =
                            classify(line);
                    diagnostics.transientLog(context,
                            classification.level(),
                            classification.minimumVerbosity(), line);
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

    private static OutputClassification classify(final String line) {
        if (line.contains(ERROR)) {
            return new OutputClassification(
                    DiagnosticLevel.ERROR, LogVerbosity.INFO);
        }
        if (line.contains(WARNING) || line.contains(WARN)) {
            return new OutputClassification(
                    DiagnosticLevel.WARN, LogVerbosity.INFO);
        }
        if (line.contains(TRACE)) {
            return new OutputClassification(
                    DiagnosticLevel.TRACE, LogVerbosity.DEBUG);
        }
        if (line.contains(DEBUG)) {
            return new OutputClassification(
                    DiagnosticLevel.DEBUG, LogVerbosity.DEBUG);
        }
        if (line.contains(INFO)) {
            return new OutputClassification(
                    DiagnosticLevel.INFO, LogVerbosity.DEBUG);
        }
        return new OutputClassification(
                DiagnosticLevel.DEBUG, LogVerbosity.DEBUG);
    }

    /**
     * Output level and minimum selected verbosity.
     *
     * @param level displayed level
     * @param minimumVerbosity minimum selected verbosity
     */
    private record OutputClassification(
            DiagnosticLevel level,
            LogVerbosity minimumVerbosity) {
    }
}
