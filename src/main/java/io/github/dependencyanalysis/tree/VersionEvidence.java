package io.github.dependencyanalysis.tree;

import java.util.Objects;

/** One version value and the source that contributed it. */
public final class VersionEvidence {

    /** Evidence source. */
    private final VersionEvidenceSource source;

    /** Version value. */
    private final String version;

    /**
     * Creates version evidence.
     *
     * @param evidenceSource source
     * @param versionValue version
     */
    public VersionEvidence(
            final VersionEvidenceSource evidenceSource,
            final String versionValue) {
        source = Objects.requireNonNull(
                evidenceSource, "evidenceSource");
        version = Objects.requireNonNull(
                versionValue, "versionValue");
    }

    /** @return source */
    public VersionEvidenceSource getSource() {
        return source;
    }

    /** @return version */
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(final Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VersionEvidence other)) {
            return false;
        }
        return source == other.source
                && version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, version);
    }
}
