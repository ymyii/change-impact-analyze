package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/** Command-wide immutable selection of WALA ReflectionOptions. */
public final class WalaReflectionOptions {

    /** Practical default retaining one flow-to-casts iteration. */
    private static final AnalysisOptions.ReflectionOptions DEFAULT =
            AnalysisOptions.ReflectionOptions
                    .ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD;

    /** Selected WALA value. */
    private final AnalysisOptions.ReflectionOptions value;

    private WalaReflectionOptions(
            final AnalysisOptions.ReflectionOptions selected) {
        value = Objects.requireNonNull(selected, "selected");
    }

    /** @return command default */
    public static WalaReflectionOptions defaultOptions() {
        return new WalaReflectionOptions(DEFAULT);
    }

    /**
     * Parses an exact WALA enum name, case-insensitively.
     *
     * @param input CLI value
     * @return selected options
     */
    public static WalaReflectionOptions parse(final String input) {
        final String candidate = Objects.requireNonNull(input, "input");
        return Arrays.stream(AnalysisOptions.ReflectionOptions.values())
                .filter(option -> option.name().equalsIgnoreCase(candidate))
                .findFirst()
                .map(WalaReflectionOptions::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "expected one of: " + supportedValues()));
    }

    /** @return comma-separated WALA enum names */
    public static String supportedValues() {
        return Arrays.stream(AnalysisOptions.ReflectionOptions.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    /** @return selected WALA enum value */
    AnalysisOptions.ReflectionOptions walaValue() {
        return value;
    }

    /** @return stable WALA enum name */
    public String identifier() {
        return value.name();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof WalaReflectionOptions that
                && value == that.value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return identifier();
    }
}
