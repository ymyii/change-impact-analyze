package io.github.dependencyanalysis.impact.refinement;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/** Optional algorithms that refine ChangePoints or reported impact results. */
public enum ResultRefinementAlgorithm {

    /** Query-time CHA receiver inference. */
    CHA_LOCAL_RECEIVER_INFERENCE("cha-local-receiver-inference"),

    /** ChangePoint-collection normalized SSA filtering. */
    SSA_EQUIVALENCE("ssa-equivalence");

    /** Stable CLI and evidence identifier. */
    private final String identifier;

    ResultRefinementAlgorithm(final String stableIdentifier) {
        identifier = stableIdentifier;
    }

    /**
     * Parses one stable identifier without accepting aliases.
     *
     * @param value CLI value
     * @return selected algorithm
     */
    public static ResultRefinementAlgorithm parse(final String value) {
        final String candidate = Objects.requireNonNull(value, "value");
        return Arrays.stream(values())
                .filter(algorithm -> algorithm.identifier.equalsIgnoreCase(
                        candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "expected one of: " + supportedValues()
                                + ", none"));
    }

    /** @return comma-separated stable identifiers */
    public static String supportedValues() {
        return Arrays.stream(values())
                .map(ResultRefinementAlgorithm::identifier)
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
