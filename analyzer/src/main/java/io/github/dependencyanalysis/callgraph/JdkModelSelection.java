package io.github.dependencyanalysis.callgraph;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

// Wiki: wiki/features/jdk-method-models.md - Command-wide JDK model policy.
/** Supported command-wide JDK Method Model selections. */
public enum JdkModelSelection {

    /** Exact public JDK 8 method summaries. */
    JDK8("jdk8"),

    /** Real JDK bytecode without project-owned method summaries. */
    NONE("none");

    /** Stable CLI and evidence identifier. */
    private final String identifier;

    JdkModelSelection(final String stableIdentifier) {
        identifier = stableIdentifier;
    }

    /** @return command default JDK model */
    public static JdkModelSelection defaultSelection() {
        return JDK8;
    }

    /**
     * Parses one stable CLI identifier without accepting aliases.
     *
     * @param value CLI value
     * @return selected JDK model
     */
    public static JdkModelSelection parse(final String value) {
        final String candidate = Objects.requireNonNull(value, "value");
        return Arrays.stream(values())
                .filter(selection -> selection.identifier.equalsIgnoreCase(
                        candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "expected one of: " + supportedValues()));
    }

    /** @return comma-separated stable CLI identifiers */
    public static String supportedValues() {
        return Arrays.stream(values())
                .map(JdkModelSelection::identifier)
                .collect(Collectors.joining(", "));
    }

    /** @return stable CLI and evidence identifier */
    public String identifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
