package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Immutable result for one side of
 * method decompilation evidence.
 */
public final class DecompiledMethod {

    /** Decompiled Java-like source. */
    private final String source;

    /** Failure reason when unavailable. */
    private final String failureReason;

    private DecompiledMethod(
            final String sourceText,
            final String reason) {
        this.source = sourceText;
        this.failureReason = reason;
    }

    /**
     * Creates available decompiled output.
     *
     * @param sourceText Java-like source
     * @return available result
     */
    public static DecompiledMethod available(
            final String sourceText) {
        final String value = Objects.requireNonNull(
                sourceText, "source");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "source must not be blank");
        }
        return new DecompiledMethod(value, null);
    }

    /**
     * Creates unavailable decompiled output.
     *
     * @param reason failure reason
     * @return unavailable result
     */
    public static DecompiledMethod unavailable(
            final String reason) {
        final String value = Objects.requireNonNull(
                reason, "failureReason");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "failureReason must not be blank");
        }
        return new DecompiledMethod(null, value);
    }

    /**
     * Returns whether source is available.
     *
     * @return true when decompilation succeeded
     */
    public boolean isAvailable() {
        return source != null;
    }

    /**
     * Returns Java-like source.
     *
     * @return source or null
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns failure reason.
     *
     * @return reason or null
     */
    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DecompiledMethod)) {
            return false;
        }
        final DecompiledMethod that =
                (DecompiledMethod) other;
        return Objects.equals(source, that.source)
                && Objects.equals(failureReason,
                that.failureReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, failureReason);
    }
}
