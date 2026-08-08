package io.github.dependencyanalysis.impact;

import java.util.Objects;

/** Legacy textual evidence wrapped behind the typed evidence boundary. */
public final class TextImpactEvidence implements ImpactEvidence {

    /** Stable text. */
    private final String text;

    /** @param value stable evidence text */
    public TextImpactEvidence(final String value) {
        text = Objects.requireNonNull(value, "value");
    }

    @Override
    public String stableKey() {
        return text;
    }

    @Override
    public String render() {
        return text;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof TextImpactEvidence that
                && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }
}
