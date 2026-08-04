package io.github.dependencyanalysis.runtime;

import java.util.Objects;

/** User-facing Maven runtime and probed-version evidence. */
public final class MavenRuntimeEvidence {

    /** Embedded Maven EOL version. */
    private static final String EOL_VERSION = "3.6.3";

    private MavenRuntimeEvidence() {
    }

    /**
     * Renders runtime source without exposing an executable path.
     *
     * @param runtime selected runtime
     * @return source evidence
     */
    public static String source(final MavenRuntimeDescriptor runtime) {
        return "source=" + Objects.requireNonNull(runtime, "runtime")
                .getSource();
    }

    /**
     * Renders the actual probed version and a conditional EOL warning.
     *
     * @param version actual Maven version
     * @return version evidence
     */
    public static String version(final MavenVersion version) {
        final String value = Objects.requireNonNull(
                version, "version").toString();
        return value + (EOL_VERSION.equals(value)
                ? "; Maven 3.6.3 is EOL" : "");
    }
}
