package io.github.dependencyanalysis.callgraph.protocol;

import java.util.Objects;

/**
 * Typed immutable fixed-point model limitation.
 *
 * @param model reporting model
 * @param code stable machine-readable code
 * @param location stable caller/resource location
 * @param detail diagnostic detail
 */
public record ModelLimitation(
        ModelKind model,
        String code,
        String location,
        String detail) implements Comparable<ModelLimitation> {

    /** Validates the typed limitation. */
    public ModelLimitation {
        Objects.requireNonNull(model, "model");
        requireText(code, "code");
        requireText(location, "location");
        requireText(detail, "detail");
    }

    private static void requireText(
            final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /** @return deterministic limitation identity */
    public String stableKey() {
        return model + "|" + code + "|" + location + "|" + detail;
    }

    /** @return stable diagnostic summary */
    public String summary() {
        return code + ": model=" + model + "; location=" + location
                + "; detail=" + detail;
    }

    @Override
    public int compareTo(final ModelLimitation other) {
        return stableKey().compareTo(other.stableKey());
    }
}
