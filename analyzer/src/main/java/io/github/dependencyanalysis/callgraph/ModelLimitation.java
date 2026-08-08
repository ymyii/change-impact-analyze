package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.impact.CoverageLimitation;
import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

import java.util.Objects;

/**
 * Typed immutable fixed-point model limitation.
 *
 * @param model reporting model
 * @param code stable machine-readable code
 * @param reason module coverage reason
 * @param location stable caller/resource location
 * @param detail diagnostic detail
 */
public record ModelLimitation(
        ModelKind model,
        String code,
        ModuleAnalysisReason reason,
        String location,
        String detail) implements CoverageLimitation,
        Comparable<ModelLimitation> {

    /** Validates the typed limitation. */
    public ModelLimitation {
        Objects.requireNonNull(model, "model");
        requireText(code, "code");
        Objects.requireNonNull(reason, "reason");
        requireText(location, "location");
        requireText(detail, "detail");
        if (reason == ModuleAnalysisReason.NONE) {
            throw new IllegalArgumentException(
                    "Model limitation must be inconclusive");
        }
        final ModuleAnalysisReason expected = switch (model) {
            case INVOKEDYNAMIC -> ModuleAnalysisReason
                    .INCONCLUSIVE_INVOKEDYNAMIC_MODEL;
            case METHOD_HANDLE -> ModuleAnalysisReason
                    .INCONCLUSIVE_METHOD_HANDLE_MODEL;
            case SERVICE_LOADER -> ModuleAnalysisReason
                    .INCONCLUSIVE_SERVICE_LOADER;
        };
        if (reason != expected) {
            throw new IllegalArgumentException(
                    "Reason " + reason + " does not match model " + model);
        }
    }

    private static void requireText(
            final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public String stableKey() {
        return model + "|" + code + "|" + location + "|" + detail;
    }

    @Override
    public String summary() {
        return code + ": model=" + model + "; location=" + location
                + "; detail=" + detail;
    }

    @Override
    public int compareTo(final ModelLimitation other) {
        return stableKey().compareTo(other.stableKey());
    }
}
