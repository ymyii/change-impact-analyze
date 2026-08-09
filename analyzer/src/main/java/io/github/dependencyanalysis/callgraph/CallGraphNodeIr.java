package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/**
 * WALA intermediate representation evidence for one exact CGNode.
 *
 * @param text printable IR, empty when unavailable
 * @param reason unavailability reason, empty when available
 */
public record CallGraphNodeIr(String text, String reason) {

    /** Validates IR evidence fields. */
    public CallGraphNodeIr {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reason, "reason");
    }

    /** @return true when printable IR is available */
    public boolean isAvailable() {
        return !text.isEmpty();
    }
}
