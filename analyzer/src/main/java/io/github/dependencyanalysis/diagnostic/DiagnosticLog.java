package io.github.dependencyanalysis.diagnostic;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

// Wiki: wiki/features/cli-preflight-diagnostics.md - 统一 Diagnostic 日志入口
/** Unified retained Diagnostic event and transient Console façade. */
public final class DiagnosticLog {

    /** Nanoseconds per millisecond. */
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    /** Retained events. */
    private final List<DiagnosticEvent> events = new ArrayList<>();

    /** Active stage monotonic starts. */
    private final Map<String, Long> stageStarts = new HashMap<>();

    /** Console destination. */
    private final PrintStream output;

    /** Wall clock. */
    private final Clock clock;

    /** Monotonic time source. */
    private final LongSupplier nanoTime;

    /** Formatter. */
    private final DiagnosticLogFormatter formatter;

    /** Active verbosity. */
    private LogVerbosity verbosity;

    /** Creates an INFO logger writing to stderr. */
    public DiagnosticLog() {
        this(System.err, LogVerbosity.INFO);
    }

    /**
     * Creates a logger.
     *
     * @param destination stderr-compatible destination
     * @param logVerbosity active verbosity
     */
    public DiagnosticLog(
            final PrintStream destination,
            final LogVerbosity logVerbosity) {
        this(destination, logVerbosity, Clock.systemDefaultZone(),
                System::nanoTime, new DiagnosticLogFormatter());
    }

    /**
     * Creates a logger with injectable time sources.
     *
     * @param destination Console destination
     * @param logVerbosity active verbosity
     * @param wallClock wall clock
     * @param monotonicTime monotonic nanosecond source
     * @param logFormatter formatter
     */
    public DiagnosticLog(
            final PrintStream destination,
            final LogVerbosity logVerbosity,
            final Clock wallClock,
            final LongSupplier monotonicTime,
            final DiagnosticLogFormatter logFormatter) {
        output = Objects.requireNonNull(destination, "output");
        verbosity = Objects.requireNonNull(logVerbosity, "verbosity");
        clock = Objects.requireNonNull(wallClock, "clock");
        nanoTime = Objects.requireNonNull(monotonicTime, "nanoTime");
        formatter = Objects.requireNonNull(logFormatter, "formatter");
    }

    /**
     * Updates verbosity before command execution starts.
     *
     * @param value verbosity
     */
    public synchronized void setVerbosity(final LogVerbosity value) {
        verbosity = Objects.requireNonNull(value, "verbosity");
    }

    /** @return active verbosity */
    public synchronized LogVerbosity getVerbosity() {
        return verbosity;
    }

    /**
     * Starts one stage-only timer and emits an INFO event.
     *
     * @param stage stage
     */
    public synchronized void startStage(final String stage) {
        final DiagnosticContext context = DiagnosticContext.stage(stage);
        startStage(context);
    }

    /**
     * Starts one complete-context timer and emits an INFO event.
     *
     * @param context context
     */
    public synchronized void startStage(final DiagnosticContext context) {
        startStage(context, "Task started");
    }

    /**
     * Starts one complete-context timer with a caller-supplied message.
     *
     * @param context context
     * @param message message
     */
    public synchronized void startStage(
            final DiagnosticContext context,
            final String message) {
        stageStarts.put(context.stableKey(), nanoTime.getAsLong());
        emitRetained(context, DiagnosticLevel.INFO, message);
    }

    /**
     * Ends one stage-only timer and emits an INFO event.
     *
     * @param stage stage
     */
    public synchronized void endStage(final String stage) {
        endStage(DiagnosticContext.stage(stage));
    }

    /**
     * Ends one complete-context timer and emits an INFO event.
     *
     * @param context context
     */
    public synchronized void endStage(final DiagnosticContext context) {
        endStage(context, "Task completed");
    }

    /**
     * Ends one complete-context timer with a caller-supplied message.
     *
     * @param context context
     * @param message message before elapsed details
     */
    public synchronized void endStage(
            final DiagnosticContext context,
            final String message) {
        final long elapsed = elapsedMillis(context);
        emitRetained(context, DiagnosticLevel.INFO,
                withElapsed(message, elapsed), elapsed);
    }

    /**
     * Fails one stage-only timer.
     *
     * @param stage stage
     * @param reason failure reason
     */
    public synchronized void failStage(
            final String stage,
            final String reason) {
        failStage(DiagnosticContext.stage(stage), reason);
    }

    /**
     * Fails one complete-context timer.
     *
     * @param context context
     * @param reason failure reason
     */
    public synchronized void failStage(
            final DiagnosticContext context,
            final String reason) {
        final long elapsed = elapsedMillis(context);
        emitRetained(context, DiagnosticLevel.ERROR,
                withElapsed(reason, elapsed), elapsed);
    }

    /**
     * Emits retained INFO.
     *
     * @param stage stage
     * @param message message
     */
    public synchronized void info(final String stage, final String message) {
        info(DiagnosticContext.stage(stage), message);
    }

    /**
     * Emits retained INFO.
     *
     * @param context context
     * @param message message
     */
    public synchronized void info(
            final DiagnosticContext context,
            final String message) {
        emitRetained(context, DiagnosticLevel.INFO, message);
    }

    /**
     * Emits retained DEBUG.
     *
     * @param stage stage
     * @param message message
     */
    public synchronized void debug(final String stage, final String message) {
        debug(DiagnosticContext.stage(stage), message);
    }

    /**
     * Emits retained DEBUG.
     *
     * @param context context
     * @param message message
     */
    public synchronized void debug(
            final DiagnosticContext context,
            final String message) {
        emitRetained(context, DiagnosticLevel.DEBUG, message);
    }

    /**
     * Emits retained TRACE.
     *
     * @param stage stage
     * @param message message
     */
    public synchronized void trace(final String stage, final String message) {
        trace(DiagnosticContext.stage(stage), message);
    }

    /**
     * Emits retained TRACE.
     *
     * @param context context
     * @param message message
     */
    public synchronized void trace(
            final DiagnosticContext context,
            final String message) {
        emitRetained(context, DiagnosticLevel.TRACE, message);
    }

    /**
     * Emits retained WARN.
     *
     * @param stage stage
     * @param message message
     */
    public synchronized void warn(final String stage, final String message) {
        warn(DiagnosticContext.stage(stage), message);
    }

    /**
     * Emits retained WARN.
     *
     * @param context context
     * @param message message
     */
    public synchronized void warn(
            final DiagnosticContext context,
            final String message) {
        emitRetained(context, DiagnosticLevel.WARN, message);
    }

    /**
     * Emits retained ERROR.
     *
     * @param stage stage
     * @param message message
     */
    public synchronized void error(final String stage, final String message) {
        error(DiagnosticContext.stage(stage), message);
    }

    /**
     * Emits retained ERROR.
     *
     * @param context context
     * @param message message
     */
    public synchronized void error(
            final DiagnosticContext context,
            final String message) {
        emitRetained(context, DiagnosticLevel.ERROR, message);
    }

    /**
     * Emits Console-only lines with independent severity and visibility.
     *
     * @param context context
     * @param level displayed level
     * @param minimumVerbosity minimum selected verbosity
     * @param message one or more physical lines
     */
    public synchronized void transientLog(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final LogVerbosity minimumVerbosity,
            final String message) {
        if (!verbosity.includes(minimumVerbosity)) {
            return;
        }
        emitLines(context, level, message, false);
    }

    /**
     * Emits a DEBUG stack trace as transient prefixed lines.
     *
     * @param context context
     * @param failure failure
     */
    public synchronized void transientException(
            final DiagnosticContext context,
            final Throwable failure) {
        if (!verbosity.includes(LogVerbosity.DEBUG)) {
            return;
        }
        final StringWriter buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        emitLines(context, DiagnosticLevel.DEBUG, buffer.toString(), false);
    }

    /**
     * Emits one retained WARN followed atomically by a transient DEBUG
     * stack trace when DEBUG verbosity is enabled.
     *
     * @param context context
     * @param message complete warning message
     * @param failure failure with cause chain
     */
    public synchronized void warnException(
            final DiagnosticContext context,
            final String message,
            final Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        warn(context, message);
        transientException(context, failure);
    }

    /** @return immutable retained event snapshot */
    public synchronized List<DiagnosticEvent> getEvents() {
        return List.copyOf(events);
    }

    private long elapsedMillis(final DiagnosticContext context) {
        final Long started = stageStarts.remove(context.stableKey());
        return started == null ? 0L
                : Math.max(0L, (nanoTime.getAsLong() - started)
                        / NANOS_PER_MILLISECOND);
    }

    private String withElapsed(final String message, final long elapsed) {
        final String normalized = Objects.requireNonNullElse(message, "");
        return normalized.isBlank()
                ? "elapsedMs=" + elapsed
                : normalized + "; elapsedMs=" + elapsed;
    }

    private void emitRetained(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message) {
        emitRetained(context, level, message, 0L);
    }

    private void emitRetained(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message,
            final long elapsedMillis) {
        if (!verbosity.includes(minimumVerbosity(level))) {
            return;
        }
        emitLines(context, level, message, true, elapsedMillis);
    }

    private void emitLines(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message,
            final boolean retain) {
        emitLines(context, level, message, retain, 0L);
    }

    private void emitLines(
            final DiagnosticContext context,
            final DiagnosticLevel level,
            final String message,
            final boolean retain,
            final long elapsedMillis) {
        final String normalized = Objects.requireNonNullElse(message, "")
                .replace("\r\n", "\n").replace('\r', '\n');
        final String[] lines = normalized.split("\n", -1);
        final int limit = lines.length > 1 && lines[lines.length - 1].isEmpty()
                ? lines.length - 1 : lines.length;
        for (int index = 0; index < limit; index++) {
            final DiagnosticEvent event = new DiagnosticEvent(
                    OffsetDateTime.now(clock), context, level, lines[index],
                    elapsedMillis);
            if (retain) {
                events.add(event);
            }
            output.println(formatter.format(event));
        }
    }

    private LogVerbosity minimumVerbosity(final DiagnosticLevel level) {
        return switch (level) {
            case TRACE -> LogVerbosity.TRACE;
            case DEBUG -> LogVerbosity.DEBUG;
            default -> LogVerbosity.INFO;
        };
    }
}
