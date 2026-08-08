package io.github.dependencyanalysis.models.jdk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads and validates the committed exact-method catalog. */
final class JdkModelCatalog {

    /** Catalog resource. */
    static final String RESOURCE =
            "/io/github/dependencyanalysis/models/jdk/jdk-models.tsv";

    /** Expected number of tab-separated fields. */
    private static final int FIELD_COUNT = 9;

    /** Static flag field position. */
    private static final int STATIC_FIELD = 3;

    /** Template field position. */
    private static final int TEMPLATE_FIELD = 4;

    /** Data argument field position. */
    private static final int DATA_ARGUMENT_FIELD = 5;

    /** State slot field position. */
    private static final int STATE_SLOT_FIELD = 6;

    /** Callback field position. */
    private static final int CALLBACK_FIELD = 7;

    /** Result slot field position. */
    private static final int RESULT_SLOT_FIELD = 8;

    List<CatalogEntry> load() {
        try (InputStream input = JdkModelCatalog.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new JdkModelException(
                        "JDK model catalog is unavailable: " + RESOURCE);
            }
            return parse(input);
        } catch (IOException exception) {
            throw new JdkModelException(
                    "Unable to close JDK model catalog", exception);
        }
    }

    List<CatalogEntry> parse(final InputStream input) {
        final List<CatalogEntry> result = new ArrayList<>();
        final Map<String, Integer> identities = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final CatalogEntry entry = entry(line, lineNumber);
                final Integer previous = identities.putIfAbsent(
                        entry.identity(), lineNumber);
                if (previous != null) {
                    throw new JdkModelException(
                            "Duplicate catalog target at lines "
                                    + previous + " and " + lineNumber
                                    + ": " + entry.identity());
                }
                result.add(entry);
            }
        } catch (IOException exception) {
            throw new JdkModelException(
                    "Unable to read JDK model catalog", exception);
        }
        if (result.isEmpty()) {
            throw new JdkModelException("JDK model catalog is empty");
        }
        return List.copyOf(result);
    }

    private CatalogEntry entry(
            final String line,
            final int lineNumber) {
        final String[] fields = line.split("\\t", -1);
        if (fields.length != FIELD_COUNT) {
            throw new JdkModelException(
                    "Expected " + FIELD_COUNT + " fields at catalog line "
                            + lineNumber + "; found " + fields.length);
        }
        try {
            return new CatalogEntry(fields[0], fields[1], fields[2],
                    Boolean.parseBoolean(fields[STATIC_FIELD]),
                    SummaryTemplate.valueOf(fields[TEMPLATE_FIELD]),
                    integer(fields[DATA_ARGUMENT_FIELD]),
                    slot(fields[STATE_SLOT_FIELD]),
                    CallbackSpec.parseAll(fields[CALLBACK_FIELD]),
                    slot(fields[RESULT_SLOT_FIELD]));
        } catch (IllegalArgumentException exception) {
            throw new JdkModelException(
                    "Invalid catalog line " + lineNumber + ": " + line,
                    exception);
        }
    }

    private int integer(final String value) {
        return "-".equals(value) ? -1 : Integer.parseInt(value);
    }

    private StateSlot slot(final String value) {
        return "-".equals(value) ? null : StateSlot.valueOf(value);
    }
}
