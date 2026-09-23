package io.github.dependencyanalysis.diagnostic;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Formats every Analyzer diagnostic with the stable five-part prefix. */
public final class DiagnosticLogFormatter {

    /** Fixed millisecond ISO offset timestamp. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    /** Canonical prefix identity order. */
    private static final List<String> ATTRIBUTE_ORDER = List.of(
            "check", "reactor", "module", "artifact", "pool");

    /** Attribute ranks. */
    private static final Map<String, Integer> ATTRIBUTE_RANKS = ranks();

    /**
     * Formats one complete physical line.
     *
     * @param event event
     * @return formatted line
     */
    public String format(final DiagnosticEvent event) {
        return formatPrefix(event) + " " + event.getMessage();
    }

    /**
     * Formats only the five prefix segments.
     *
     * @param event event
     * @return formatted prefix
     */
    public String formatPrefix(final DiagnosticEvent event) {
        return "[" + TIMESTAMP.format(event.getTimestamp()) + "]"
                + "[" + event.getLevel() + "]"
                + segment(event.getStage())
                + segment(event.getSubstage())
                + context(event.getPhase(), event.getAttributes());
    }

    /**
     * Formats a subprocess source without assigning an Analyzer severity.
     * @param context operation source
     * @return source prefix
     */
    public String formatProcessSource(final DiagnosticContext context) {
        return "[process]" + segment(context.stage())
                + segment(context.substage())
                + context(context.phase(), context.attributes());
    }

    private String segment(final String value) {
        if (value == null || value.isBlank()) {
            return "[-]";
        }
        return "[" + escape(value) + "]";
    }

    private String context(
            final String phase,
            final Map<String, String> values) {
        final List<String> entries = new ArrayList<>();
        if (phase != null && !phase.isBlank()) {
            entries.add("phase=" + escape(phase));
        }
        values.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .sorted(attributeComparator())
                .map(entry -> escape(entry.getKey()) + "="
                        + escape(entry.getValue()))
                .forEach(entries::add);
        if (entries.isEmpty()) {
            return "[-]";
        }
        return "[" + String.join(";", entries) + "]";
    }

    private Comparator<Map.Entry<String, String>> attributeComparator() {
        return Comparator
                .comparingInt((Map.Entry<String, String> entry) ->
                        ATTRIBUTE_RANKS.getOrDefault(
                                entry.getKey(), Integer.MAX_VALUE))
                .thenComparing(Map.Entry::getKey);
    }

    private String escape(final String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace("=", "\\=")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private static Map<String, Integer> ranks() {
        final Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < ATTRIBUTE_ORDER.size(); index++) {
            result.put(ATTRIBUTE_ORDER.get(index), index);
        }
        return Map.copyOf(result);
    }
}
