package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Stable source location for terminal evidence.
 *
 * @param source caller or resource identity
 * @param bytecodePc bytecode PC, or -1 when not applicable
 */
public record EvidenceLocation(String source, int bytecodePc) {

    /** Validates source identity. */
    public EvidenceLocation {
        Objects.requireNonNull(source, "source");
    }

    /** @return stable source identity */
    public String stableKey() {
        return source + "|pc=" + bytecodePc;
    }
}
