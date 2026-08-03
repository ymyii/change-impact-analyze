package io.github.dependencyanalysis.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DiagnosticCollector}.
 */
class DiagnosticCollectorTest {

    /** Expected event count in multi-event tests. */
    private static final int THREE_EVENTS = 3;

    /** Expected independent context event count. */
    private static final int FOUR_EVENTS = 4;

    @Test
    void startStageEmitsEvent() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.startStage("validation");
        assertThat(c.getEvents())
                .anyMatch(e ->
                        "validation".equals(e.getStage())
                        && e.getLevel()
                                == DiagnosticLevel.INFO
                        && e.getMessage()
                                .contains("started"));
    }

    @Test
    void endStageEmitsEventWithElapsed() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.startStage("validation");
        c.endStage("validation");
        final List<DiagnosticEvent> events =
                c.getEvents();
        assertThat(events)
                .anyMatch(e ->
                        "validation".equals(e.getStage())
                        && e.getLevel()
                                == DiagnosticLevel.INFO
                        && e.getMessage().contains("ended")
                        && e.getElapsedMillis() >= 0);
    }

    @Test
    void failStageEmitsErrorEvent() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.startStage("validation");
        c.failStage("validation", "bad input");
        assertThat(c.getEvents())
                .anyMatch(e ->
                        "validation".equals(e.getStage())
                        && e.getLevel()
                                == DiagnosticLevel.ERROR
                        && "bad input".equals(
                                e.getMessage()));
    }

    @Test
    void infoWarnErrorEmitCorrectLevel() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.info("s", "i");
        c.warn("s", "w");
        c.error("s", "e");
        final List<DiagnosticEvent> events =
                c.getEvents();
        assertThat(events).hasSize(THREE_EVENTS);
        assertThat(events.get(0).getLevel())
                .isEqualTo(DiagnosticLevel.INFO);
        assertThat(events.get(1).getLevel())
                .isEqualTo(DiagnosticLevel.WARN);
        assertThat(events.get(2).getLevel())
                .isEqualTo(DiagnosticLevel.ERROR);
    }

    @Test
    void getEventsReturnsUnmodifiableList() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.info("s", "msg");
        final List<DiagnosticEvent> events =
                c.getEvents();
        assertThatThrownBy(() ->
                events.add(new DiagnosticEvent.Builder()
                        .build()))
                .isInstanceOf(
                        UnsupportedOperationException
                                .class);
    }

    @Test
    void eventsPreserveInsertionOrder() {
        final DiagnosticCollector c =
                new DiagnosticCollector();
        c.info("a", "first");
        c.info("b", "second");
        c.info("c", "third");
        final List<DiagnosticEvent> events =
                c.getEvents();
        assertThat(events).hasSize(THREE_EVENTS);
        assertThat(events.get(0).getStage())
                .isEqualTo("a");
        assertThat(events.get(1).getStage())
                .isEqualTo("b");
        assertThat(events.get(2).getStage())
                .isEqualTo("c");
    }

    @Test
    void defaultInfoHidesDebugAndTrace() {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(
                bytes, true, StandardCharsets.UTF_8);
        final DiagnosticCollector collector =
                new DiagnosticCollector(stream, stream);

        collector.info("stage", "info");
        collector.debug("stage", "debug");
        collector.trace("stage", "trace");

        assertThat(collector.getEvents())
                .extracting(DiagnosticEvent::getLevel)
                .containsExactly(DiagnosticLevel.INFO);
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("info")
                .doesNotContain("debug", "trace");
    }

    @Test
    void debugAndTraceVerbosityEnableExpectedDetail() {
        final ByteArrayOutputStream debugBytes =
                new ByteArrayOutputStream();
        final ByteArrayOutputStream traceBytes =
                new ByteArrayOutputStream();
        final DiagnosticCollector debug =
                new DiagnosticCollector(
                        new PrintStream(debugBytes),
                        new PrintStream(debugBytes),
                        LogVerbosity.DEBUG);
        final DiagnosticCollector trace =
                new DiagnosticCollector(
                        new PrintStream(traceBytes),
                        new PrintStream(traceBytes),
                        LogVerbosity.TRACE);

        debug.debug("stage", "debug-message");
        debug.trace("stage", "trace-message");
        trace.debug("stage", "debug-message");
        trace.trace("stage", "trace-message");

        assertThat(debug.getEvents())
                .extracting(DiagnosticEvent::getLevel)
                .containsExactly(DiagnosticLevel.DEBUG);
        assertThat(debugBytes.toString(StandardCharsets.UTF_8))
                .contains("[DEBUG] debug-message")
                .doesNotContain("trace-message");
        assertThat(trace.getEvents())
                .extracting(DiagnosticEvent::getLevel)
                .containsExactly(DiagnosticLevel.DEBUG,
                        DiagnosticLevel.TRACE);
        assertThat(traceBytes.toString(StandardCharsets.UTF_8))
                .contains("[DEBUG] debug-message")
                .contains("[TRACE] trace-message");
    }

    @Test
    void concurrentContextsUseStablePrefixAndIndependentTiming() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(bytes);
        final DiagnosticCollector collector =
                new DiagnosticCollector(stream, stream);
        final DiagnosticContext first = DiagnosticContext.task(
                "front", "baseline-dependency").withSide("baseline");
        final DiagnosticContext second = DiagnosticContext.task(
                "front", "target-build").withSide("target");

        collector.startStage(first);
        collector.startStage(second);
        collector.endStage(first);
        collector.endStage(second);

        assertThat(collector.getEvents()).hasSize(FOUR_EVENTS);
        assertThat(collector.getEvents().stream()
                .filter(event -> "Task completed".equals(
                        event.getMessage()))
                .map(DiagnosticEvent::getTask))
                .containsExactly("baseline-dependency", "target-build");
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[front][baseline-dependency][side=baseline]")
                .contains("[front][target-build][side=target]");
    }
}
