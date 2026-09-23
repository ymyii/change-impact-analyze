package io.github.dependencyanalysis.util;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

// Wiki: wiki/rules/operational-evidence-design.md - 外部日志实时转发
/** Drains both subprocess streams concurrently without caching logs. */
public final class ProcessConsoleExecutor {

    /** Character buffer for command return data. */
    private static final int DATA_BUFFER_SIZE = 4096;

    /** Private utility constructor. */
    private ProcessConsoleExecutor() {
    }

    /**
     * Streams both output channels to the Console.
     * @param builder resolved process builder
     * @param diagnostics Console destination
     * @param context operation source
     * @return exit status without retained logs
     * @throws IOException on process or reader failure
     * @throws InterruptedException when interrupted
     */
    public static ProcessConsoleResult execute(
            final ProcessBuilder builder,
            final DiagnosticLog diagnostics,
            final DiagnosticContext context)
            throws IOException, InterruptedException {
        return run(builder, diagnostics, context, false);
    }

    /**
     * Captures stdout as return data and streams stderr to the Console.
     * @param builder resolved process builder
     * @param diagnostics Console destination
     * @param context operation source
     * @return exit status and stdout data
     * @throws IOException on process or reader failure
     * @throws InterruptedException when interrupted
     */
    public static ProcessConsoleResult executeData(
            final ProcessBuilder builder,
            final DiagnosticLog diagnostics,
            final DiagnosticContext context)
            throws IOException, InterruptedException {
        return run(builder, diagnostics, context, true);
    }

    private static ProcessConsoleResult run(
            final ProcessBuilder builder,
            final DiagnosticLog diagnostics,
            final DiagnosticContext context,
            final boolean captureData)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(false);
        final Process process = builder.start();
        final StringBuilder data = captureData ? new StringBuilder() : null;
        final AtomicReference<IOException> failure = new AtomicReference<>();
        final DiagnosticContext source = context.with("pid", process.pid());
        final Thread stdout = pump(process, process.getInputStream(),
                diagnostics, source.with("stream", "stdout"), data, failure);
        final Thread stderr = pump(process, process.getErrorStream(),
                diagnostics, source.with("stream", "stderr"), null, failure);
        try {
            process.getOutputStream().close();
            final int exitCode = process.waitFor();
            stdout.join();
            stderr.join();
            if (failure.get() != null) {
                throw failure.get();
            }
            return new ProcessConsoleResult(exitCode,
                    data == null ? "" : data.toString());
        } catch (IOException | InterruptedException exception) {
            ProcessTreeTerminator.terminate(process);
            throw exception;
        } finally {
            process.getInputStream().close();
            process.getErrorStream().close();
            stdout.interrupt();
            stderr.interrupt();
        }
    }

    private static Thread pump(
            final Process process,
            final InputStream input,
            final DiagnosticLog diagnostics,
            final DiagnosticContext source,
            final StringBuilder data,
            final AtomicReference<IOException> failure) {
        final Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                if (data != null) {
                    final char[] buffer = new char[DATA_BUFFER_SIZE];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        data.append(buffer, 0, count);
                    }
                } else {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        diagnostics.processOutput(source, line);
                    }
                }
            } catch (IOException exception) {
                failure.compareAndSet(null, exception);
                ProcessTreeTerminator.terminate(process);
            }
        }, "dependency-analyzer-process-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
