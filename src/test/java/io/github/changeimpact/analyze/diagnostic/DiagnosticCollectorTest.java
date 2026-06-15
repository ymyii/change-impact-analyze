package io.github.changeimpact.analyze.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DiagnosticCollector}.
 */
class DiagnosticCollectorTest {

    /** Expected event count in multi-event tests. */
    private static final int THREE_EVENTS = 3;

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
}
