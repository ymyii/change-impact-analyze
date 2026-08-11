package io.github.dependencyanalysis.callgraph;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Unique exhaustive algorithm-to-strategy mapping. */
final class CallGraphStrategyFactory {

    /** Immutable strategy registry. */
    private final Map<CallGraphAlgorithm, CallGraphAlgorithmStrategy>
            strategies;

    CallGraphStrategyFactory() {
        final EnumMap<CallGraphAlgorithm, CallGraphAlgorithmStrategy> values =
                new EnumMap<>(CallGraphAlgorithm.class);
        register(values, new RtaCallGraphStrategy());
        register(values, new ZeroCfaCallGraphStrategy());
        register(values, new OptimizedZeroOneCfaCallGraphStrategy());
        register(values, new KObjCallGraphStrategy());
        if (values.size() != CallGraphAlgorithm.values().length) {
            throw new IllegalStateException(
                    "Missing Call Graph algorithm strategy");
        }
        strategies = Map.copyOf(values);
    }

    CallGraphAlgorithmStrategy create(
            final CallGraphAlgorithm algorithm) {
        final CallGraphAlgorithmStrategy result = strategies.get(
                Objects.requireNonNull(algorithm, "algorithm"));
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unsupported Call Graph algorithm: " + algorithm);
        }
        return result;
    }

    private void register(
            final EnumMap<CallGraphAlgorithm, CallGraphAlgorithmStrategy>
                    values,
            final CallGraphAlgorithmStrategy strategy) {
        if (values.put(strategy.algorithm(), strategy) != null) {
            throw new IllegalStateException(
                    "Duplicate Call Graph algorithm strategy: "
                            + strategy.algorithm());
        }
    }
}
