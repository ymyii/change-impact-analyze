package io.github.dependencyanalysis.diagnostic;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects diagnostic events and tracks task timing.
 */
public final class DiagnosticCollector {

    /** Collected events. */
    private final List<DiagnosticEvent> events =
            new ArrayList<>();

    /** Context start timestamps. */
    private final Map<String, Long> stageStarts =
            new HashMap<>();

    /** Standard output stream. */
    private final PrintStream out;

    /** Standard error stream. */
    private final PrintStream err;

    /** Active verbosity. */
    private LogVerbosity verbosity;

    /** Creates a collector using system streams. */
    public DiagnosticCollector() {
        this(System.out, System.err,
                LogVerbosity.INFO);
    }

    /**
     * Creates a collector with custom streams.
     *
     * @param outStream standard output stream
     * @param errStream standard error stream
     */
    public DiagnosticCollector(final PrintStream outStream,
                               final PrintStream errStream) {
        this(outStream, errStream,
                LogVerbosity.INFO);
    }

    /**
     * Creates a collector with custom streams and verbosity.
     *
     * @param outStream standard output stream
     * @param errStream standard error stream
     * @param logVerbosity active verbosity
     */
    public DiagnosticCollector(final PrintStream outStream,
                               final PrintStream errStream,
                               final LogVerbosity logVerbosity) {
        this.out = outStream;
        this.err = errStream;
        this.verbosity = logVerbosity;
    }

    /**
     * Updates the active verbosity before analysis starts.
     *
     * @param value active verbosity
     */
    public synchronized void setVerbosity(
            final LogVerbosity value) {
        verbosity = value;
    }

    /** @param stage stage name */
    public synchronized void startStage(
            final String stage) {
        final DiagnosticContext context = DiagnosticContext.stage(stage);
        stageStarts.put(context.stableKey(),
                System.currentTimeMillis());
        emit(context, DiagnosticLevel.INFO,
                "Stage started: " + stage, 0L);
    }

    /** @param context task context */
    public synchronized void startStage(
            final DiagnosticContext context) {
        stageStarts.put(context.stableKey(),
                System.currentTimeMillis());
        emit(context, DiagnosticLevel.INFO,
                "Task started", 0L);
    }

    /** @param stage stage name */
    public synchronized void endStage(
            final String stage) {
        final DiagnosticContext context = DiagnosticContext.stage(stage);
        final long elapsed = computeElapsed(context);
        emit(context, DiagnosticLevel.INFO,
                "Stage ended: " + stage, elapsed);
    }

    /** @param context task context */
    public synchronized void endStage(
            final DiagnosticContext context) {
        final long elapsed = computeElapsed(context);
        emit(context, DiagnosticLevel.INFO,
                "Task completed", elapsed);
    }

    /**
     * @param stage stage name
     * @param reason failure reason
     */
    public synchronized void failStage(
            final String stage, final String reason) {
        failStage(DiagnosticContext.stage(stage),
                reason);
    }

    /**
     * @param context task context
     * @param reason failure reason
     */
    public synchronized void failStage(
            final DiagnosticContext context,
            final String reason) {
        final long elapsed = computeElapsed(context);
        emit(context, DiagnosticLevel.ERROR,
                reason, elapsed);
    }

    /**
     * @param stage stage
     * @param message message
     */
    public synchronized void info(final String stage,
                                  final String message) {
        info(DiagnosticContext.stage(stage), message);
    }

    /**
     * @param context context
     * @param message message
     */
    public synchronized void info(
            final DiagnosticContext context,
            final String message) {
        emit(context, DiagnosticLevel.INFO,
                message, 0L);
    }

    /**
     * @param stage stage
     * @param message message
     */
    public synchronized void debug(final String stage,
                                   final String message) {
        debug(DiagnosticContext.stage(stage), message);
    }

    /**
     * @param context context
     * @param message message
     */
    public synchronized void debug(
            final DiagnosticContext context,
            final String message) {
        emit(context, DiagnosticLevel.DEBUG,
                message, 0L);
    }

    /**
     * @param stage stage
     * @param message message
     */
    public synchronized void trace(final String stage,
                                   final String message) {
        trace(DiagnosticContext.stage(stage), message);
    }

    /**
     * @param context context
     * @param message message
     */
    public synchronized void trace(
            final DiagnosticContext context,
            final String message) {
        emit(context, DiagnosticLevel.TRACE,
                message, 0L);
    }

    /**
     * @param stage stage
     * @param message message
     */
    public synchronized void warn(final String stage,
                                  final String message) {
        warn(DiagnosticContext.stage(stage), message);
    }

    /**
     * @param context context
     * @param message message
     */
    public synchronized void warn(
            final DiagnosticContext context,
            final String message) {
        emit(context, DiagnosticLevel.WARN,
                message, 0L);
    }

    /**
     * @param stage stage
     * @param message message
     */
    public synchronized void error(final String stage,
                                   final String message) {
        error(DiagnosticContext.stage(stage), message);
    }

    /**
     * @param context context
     * @param message message
     */
    public synchronized void error(
            final DiagnosticContext context,
            final String message) {
        emit(context, DiagnosticLevel.ERROR,
                message, 0L);
    }

    /**
     * Writes transient process output to the Console without retaining it
     * in Report diagnostics.
     *
     * @param context process diagnostic context
     * @param level output level
     * @param message process output line
     */
    public synchronized void console(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message) {
        if (!isEnabled(level)) {
            return;
        }
        final DiagnosticEvent event =
                new DiagnosticEvent.Builder()
                        .stage(context.stage())
                        .task(context.task())
                        .level(level)
                        .message(message)
                        .side(context.side())
                        .module(context.module())
                        .artifact(context.artifact())
                        .path(context.path())
                        .build();
        printToConsole(event);
    }

    /**
     * @return immutable event snapshot
     */
    public synchronized List<DiagnosticEvent> getEvents() {
        return List.copyOf(events);
    }

    private long computeElapsed(
            final DiagnosticContext context) {
        final Long start = stageStarts.remove(
                context.stableKey());
        if (start == null) {
            return 0L;
        }
        return System.currentTimeMillis() - start;
    }

    private void emit(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message,
            final long elapsed) {
        if (!isEnabled(level)) {
            return;
        }
        final DiagnosticEvent event =
                new DiagnosticEvent.Builder()
                        .stage(context.stage())
                        .task(context.task())
                        .level(level)
                        .message(message)
                        .side(context.side())
                        .module(context.module())
                        .artifact(context.artifact())
                        .path(context.path())
                        .elapsedMillis(elapsed)
                        .build();
        events.add(event);
        printToConsole(event);
    }

    private void printToConsole(
            final DiagnosticEvent event) {
        final String line = prefix(event) + " "
                + detailPrefix(event.getLevel())
                + event.getMessage();
        if (event.getLevel()
                == DiagnosticLevel.ERROR) {
            err.println(line);
        } else {
            out.println(line);
        }
    }

    private String prefix(
            final DiagnosticEvent event) {
        final StringBuilder value = new StringBuilder()
                .append('[').append(event.getStage())
                .append(']');
        appendPlain(value, event.getTask());
        appendNamed(value, "side", event.getSide());
        appendNamed(value, "module", event.getModule());
        appendNamed(value, "artifact",
                event.getArtifact());
        return value.toString();
    }

    private void appendPlain(final StringBuilder value,
                             final String field) {
        if (field != null && !field.isBlank()) {
            value.append('[').append(field).append(']');
        }
    }

    private void appendNamed(final StringBuilder value,
                             final String name,
                             final String field) {
        if (field != null && !field.isBlank()) {
            value.append('[').append(name).append('=')
                    .append(field).append(']');
        }
    }

    private boolean isEnabled(
            final DiagnosticLevel level) {
        return switch (level) {
            case TRACE -> verbosity.includes(
                    LogVerbosity.TRACE);
            case DEBUG -> verbosity.includes(
                    LogVerbosity.DEBUG);
            default -> true;
        };
    }

    private String detailPrefix(
            final DiagnosticLevel level) {
        return switch (level) {
            case DEBUG, TRACE -> "[" + level + "] ";
            default -> "";
        };
    }
}
