package io.github.dependencyanalysis.impact;

import java.util.Arrays;
import java.util.Locale;

/** External dependency method-body scope requested for Call Graph analysis. */
public enum DependencyAnalysisScopeMode {

    /** Real bodies only on every reverse path to a changed dependency. */
    CHANGED_PATHS("changed-paths"),

    /** Real bodies for every external dependency. */
    FULL("full");

    /** Stable CLI identifier. */
    private final String identifier;

    DependencyAnalysisScopeMode(final String value) {
        identifier = value;
    }

    /** @return stable CLI/report identifier */
    public String identifier() {
        return identifier;
    }

    /** @return default dependency analysis scope */
    public static DependencyAnalysisScopeMode defaultMode() {
        return CHANGED_PATHS;
    }

    /**
     * Parses case-insensitive CLI spelling.
     *
     * @param value CLI value
     * @return parsed mode
     */
    public static DependencyAnalysisScopeMode parse(final String value) {
        final String normalized = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.identifier.equals(normalized))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported dependency analysis scope: " + value
                                + "; expected changed-paths or full"));
    }
}
