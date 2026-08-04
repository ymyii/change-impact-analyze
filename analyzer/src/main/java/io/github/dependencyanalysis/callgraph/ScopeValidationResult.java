package io.github.dependencyanalysis.callgraph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Non-blocking external dependency warnings produced by scope validation.
 *
 * @param warnings stable artifact-level warnings
 */
public record ScopeValidationResult(
        List<ScopeValidationWarning> warnings) {

    /** Snapshots warnings in deterministic order. */
    public ScopeValidationResult {
        warnings = Objects.requireNonNull(warnings, "warnings").stream()
                .sorted(Comparator.comparing(
                        ScopeValidationWarning::stableKey))
                .toList();
    }

    /** @return true when external dependency coverage is limited */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /** @return warning text suitable for report coverage limitations */
    public List<String> limitations() {
        return warnings.stream()
                .map(ScopeValidationWarning::summary)
                .toList();
    }
}
