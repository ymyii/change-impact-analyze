package io.github.dependencyanalysis.tree;

import java.util.Objects;

/** Maven conflict key including type and classifier. */
public final class DependencyKey
        implements Comparable<DependencyKey> {

    /** Group id. */
    private final String groupId;

    /** Artifact id. */
    private final String artifactId;

    /** Type. */
    private final String type;

    /** Classifier. */
    private final String classifier;

    /**
     * Creates a key.
     *
     * @param group groupId
     * @param artifact artifactId
     * @param dependencyType type
     * @param dependencyClassifier classifier
     */
    public DependencyKey(
            final String group,
            final String artifact,
            final String dependencyType,
            final String dependencyClassifier) {
        groupId = Objects.requireNonNull(group,
                "group");
        artifactId = Objects.requireNonNull(
                artifact, "artifact");
        type = Objects.requireNonNull(
                dependencyType, "dependencyType");
        classifier = dependencyClassifier == null
                ? "" : dependencyClassifier;
    }

    /** @return groupId */
    public String getGroupId() {
        return groupId;
    }

    /** @return artifactId */
    public String getArtifactId() {
        return artifactId;
    }

    /** @return type */
    public String getType() {
        return type;
    }

    /** @return classifier */
    public String getClassifier() {
        return classifier;
    }

    @Override
    public int compareTo(
            final DependencyKey other) {
        return toString().compareTo(
                other.toString());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DependencyKey)) {
            return false;
        }
        final DependencyKey value =
                (DependencyKey) other;
        return groupId.equals(value.groupId)
                && artifactId.equals(value.artifactId)
                && type.equals(value.type)
                && classifier.equals(value.classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId,
                type, classifier);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":"
                + type + ":" + classifier;
    }
}
