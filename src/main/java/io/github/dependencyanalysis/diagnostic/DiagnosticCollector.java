package io.github.dependencyanalysis.diagnostic;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects diagnostic events and tracks stage timing.
 */
public final class DiagnosticCollector {

    /** Collected events. */
    private final List<DiagnosticEvent> events =
            new ArrayList<>();

    /** Stage start timestamps. */
    private final Map<String, Long> stageStarts =
            new HashMap<>();

    /** Standard output stream. */
    private final PrintStream out;

    /** Standard error stream. */
    private final PrintStream err;

    /** Active verbosity. */
    private LogVerbosity verbosity;

    /**
     * Creates a collector using system streams.
     */
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

    /**
     * Starts a stage and emits an INFO event.
     *
     * @param stage stage name
     */
    public synchronized void startStage(final String stage) {
        stageStarts.put(stage,
                System.currentTimeMillis());
        emit(stage, DiagnosticLevel.INFO,
                "Stage started: " + stage, 0L);
    }

    /**
     * Ends a stage and emits an INFO event.
     *
     * @param stage stage name
     */
    public synchronized void endStage(final String stage) {
        final long elapsed = computeElapsed(stage);
        emit(stage, DiagnosticLevel.INFO,
                "Stage ended: " + stage, elapsed);
    }

    /**
     * Fails a stage and emits an ERROR event.
     *
     * @param stage  stage name
     * @param reason failure reason
     */
    public synchronized void failStage(final String stage,
                          final String reason) {
        final long elapsed = computeElapsed(stage);
        emit(stage, DiagnosticLevel.ERROR,
                reason, elapsed);
    }

    /**
     * Emits an INFO event.
     *
     * @param stage   stage name
     * @param message message
     */
    public synchronized void info(final String stage,
                     final String message) {
        emit(stage, DiagnosticLevel.INFO,
                message, 0L);
    }

    /**
     * Emits a DEBUG event when DEBUG is enabled.
     *
     * @param stage stage name
     * @param message message
     */
    public synchronized void debug(final String stage,
                                   final String message) {
        emit(stage, DiagnosticLevel.DEBUG,
                message, 0L);
    }

    /**
     * Emits a TRACE event when TRACE is enabled.
     *
     * @param stage stage name
     * @param message message
     */
    public synchronized void trace(final String stage,
                                   final String message) {
        emit(stage, DiagnosticLevel.TRACE,
                message, 0L);
    }

    /**
     * Emits a WARN event.
     *
     * @param stage   stage name
     * @param message message
     */
    public synchronized void warn(final String stage,
                     final String message) {
        emit(stage, DiagnosticLevel.WARN,
                message, 0L);
    }

    /**
     * Emits an ERROR event.
     *
     * @param stage   stage name
     * @param message message
     */
    public synchronized void error(final String stage,
                      final String message) {
        emit(stage, DiagnosticLevel.ERROR,
                message, 0L);
    }

    /**
     * Returns an unmodifiable view of events.
     *
     * @return event list
     */
    public synchronized List<DiagnosticEvent> getEvents() {
        return List.copyOf(events);
    }

    private long computeElapsed(final String stage) {
        final Long start = stageStarts.get(stage);
        if (start == null) {
            return 0L;
        }
        return System.currentTimeMillis() - start;
    }

    private void emit(final String stage,
                      final DiagnosticLevel level,
                      final String message,
                      final long elapsed) {
        if (!isEnabled(level)) {
            return;
        }
        final DiagnosticEvent event =
                new DiagnosticEvent.Builder()
                        .stage(stage)
                        .level(level)
                        .message(message)
                        .elapsedMillis(elapsed)
                        .build();
        events.add(event);
        printToConsole(event);
    }

    private void printToConsole(
            final DiagnosticEvent event) {
        final String line = "["
                + event.getStage() + "] "
                + detailPrefix(event.getLevel())
                + event.getMessage();
        if (event.getLevel()
                == DiagnosticLevel.ERROR) {
            err.println(line);
        } else {
            out.println(line);
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
