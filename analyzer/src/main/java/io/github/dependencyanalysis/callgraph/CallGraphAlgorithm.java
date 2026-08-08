package io.github.dependencyanalysis.callgraph;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

// Wiki: wiki/features/call-graph-engine.md - Command-wide algorithm policy
/** Supported command-wide WALA Call Graph algorithms. */
public enum CallGraphAlgorithm {

    /** Class-based Rapid Type Analysis. */
    RTA("rta"),

    /** Class-based allocation identity with constant-specific keys. */
    ZERO_CFA("zero-cfa"),

    /** Allocation-sensitive 0-1-CFA with bounded smushing. */
    OPTIMIZED_ZERO_ONE_CFA("optimized-0-1-cfa");

    /** Stable CLI and evidence identifier. */
    private final String identifier;

    CallGraphAlgorithm(final String stableIdentifier) {
        identifier = stableIdentifier;
    }

    /** @return command default algorithm */
    public static CallGraphAlgorithm defaultAlgorithm() {
        return RTA;
    }

    /**
     * Parses one stable CLI identifier without accepting aliases.
     *
     * @param value CLI value
     * @return selected algorithm
     */
    public static CallGraphAlgorithm parse(final String value) {
        final String candidate = Objects.requireNonNull(
                value, "value");
        return Arrays.stream(values())
                .filter(algorithm -> algorithm.identifier.equalsIgnoreCase(
                        candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "expected one of: " + supportedValues()));
    }

    /** @return comma-separated stable CLI identifiers */
    public static String supportedValues() {
        return Arrays.stream(values())
                .map(CallGraphAlgorithm::identifier)
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
