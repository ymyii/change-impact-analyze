package io.github.changeimpact.analyze.dependency;

import java.util.Objects;

// Wiki: wiki/features/dependency-tree-extraction.md - Maven artifact 坐标，依赖树基本单元
/**
 * Immutable Maven artifact coordinate
 * with groupId, artifactId, type and
 * version.
 */
public final class ArtifactCoord {

    /** Minimum label segments. */
    private static final int MIN_SEGMENTS =
            4;

    /** Maximum label segments. */
    private static final int MAX_SEGMENTS =
            5;

    /** Group id segment index. */
    private static final int IDX_GROUP =
            0;

    /** Artifact id segment index. */
    private static final int IDX_ARTIFACT =
            1;

    /** Type segment index. */
    private static final int IDX_TYPE =
            2;

    /** Version segment index. */
    private static final int IDX_VERSION =
            3;

    /** Group identifier. */
    private final String groupId;

    /** Artifact identifier. */
    private final String artifactId;

    /** Packaging type. */
    private final String type;

    /** Artifact version. */
    private final String version;

    /**
     * Creates a new artifact coordinate.
     *
     * @param group group identifier
     * @param artifact artifact identifier
     * @param pkg packaging type
     * @param ver artifact version
     */
    public ArtifactCoord(
            final String group,
            final String artifact,
            final String pkg,
            final String ver) {
        this.groupId =
                Objects.requireNonNull(
                        group, "groupId");
        this.artifactId =
                Objects.requireNonNull(
                        artifact,
                        "artifactId");
        this.type =
                Objects.requireNonNull(
                        pkg, "type");
        this.version =
                Objects.requireNonNull(
                        ver, "version");
    }

    /**
     * Parses a label string into an
     * ArtifactCoord. Supports 4-segment
     * (g:a:t:v) and 5-segment
     * (g:a:t:v:scope) formats.
     *
     * @param label colon-separated label
     * @return parsed coordinate
     * @throws IllegalArgumentException
     *  if label format is invalid
     */
    public static ArtifactCoord parse(
            final String label) {
        Objects.requireNonNull(
                label, "label");
        final String trimmed =
                label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty label");
        }
        final String[] parts =
                trimmed.split(":");
        if (parts.length < MIN_SEGMENTS
                || parts.length > MAX_SEGMENTS) {
            throw new IllegalArgumentException(
                    "Invalid label format: "
                            + label);
        }
        return new ArtifactCoord(
                parts[IDX_GROUP],
                parts[IDX_ARTIFACT],
                parts[IDX_TYPE],
                parts[IDX_VERSION]);
    }

    /**
     * Returns the group identifier.
     *
     * @return group id
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Returns the artifact identifier.
     *
     * @return artifact id
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Returns the packaging type.
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the artifact version.
     *
     * @return version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns a diff key for comparing
     * artifacts across versions.
     * Format is groupId:artifactId:type.
     *
     * @return diff key string
     */
    public String diffKey() {
        return groupId + ":"
                + artifactId + ":"
                + type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArtifactCoord)) {
            return false;
        }
        final ArtifactCoord that =
                (ArtifactCoord) o;
        return groupId.equals(that.groupId)
                && artifactId.equals(
                        that.artifactId)
                && type.equals(that.type)
                && version.equals(
                        that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                groupId, artifactId,
                type, version);
    }

    @Override
    public String toString() {
        return groupId + ":"
                + artifactId + ":"
                + type + ":"
                + version;
    }
}
