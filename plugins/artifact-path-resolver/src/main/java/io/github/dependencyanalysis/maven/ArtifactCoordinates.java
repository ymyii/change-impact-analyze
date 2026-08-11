package io.github.dependencyanalysis.maven;

import org.eclipse.aether.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;

import java.util.Comparator;
import java.util.Objects;

/** Complete Maven artifact identity exposed by the JSON contract. */
final class ArtifactCoordinates {

    /** Stable output order. */
    static final Comparator<ArtifactCoordinates> ORDER = Comparator
            .comparing(ArtifactCoordinates::getGroupId)
            .thenComparing(ArtifactCoordinates::getArtifactId)
            .thenComparing(ArtifactCoordinates::getType)
            .thenComparing(ArtifactCoordinates::getExtension)
            .thenComparing(ArtifactCoordinates::getClassifier)
            .thenComparing(ArtifactCoordinates::getVersion)
            .thenComparing(ArtifactCoordinates::getBaseVersion);

    /** Group identifier. */
    private final String groupId;

    /** Artifact identifier. */
    private final String artifactId;

    /** Maven artifact type. */
    private final String type;

    /** Physical extension. */
    private final String extension;

    /** Classifier, possibly empty. */
    private final String classifier;

    /** Resolved version. */
    private final String version;

    /** Base version used for reactor identity. */
    private final String baseVersion;

    ArtifactCoordinates(
            final String group,
            final String artifact,
            final String artifactType,
            final String artifactExtension,
            final String artifactClassifier,
            final String artifactVersion,
            final String artifactBaseVersion) {
        groupId = Objects.requireNonNull(group, "groupId");
        artifactId = Objects.requireNonNull(artifact, "artifactId");
        type = Objects.requireNonNull(artifactType, "type");
        extension = Objects.requireNonNull(
                artifactExtension, "extension");
        classifier = Objects.requireNonNull(
                artifactClassifier, "classifier");
        version = Objects.requireNonNull(artifactVersion, "version");
        baseVersion = Objects.requireNonNull(
                artifactBaseVersion, "baseVersion");
    }

    static ArtifactCoordinates from(final Artifact artifact) {
        return new ArtifactCoordinates(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                typeOf(artifact),
                artifact.getExtension(),
                emptyIfNull(artifact.getClassifier()),
                artifact.getVersion(),
                artifact.getBaseVersion());
    }

    static ArtifactCoordinates from(
            final org.apache.maven.artifact.Artifact artifact) {
        final ArtifactHandler handler = artifact.getArtifactHandler();
        final String type = emptyDefault(artifact.getType(), "jar");
        final String extension = handler == null
                ? type : emptyDefault(handler.getExtension(), type);
        final String classifier = emptyIfNull(artifact.getClassifier());
        return new ArtifactCoordinates(
                artifact.getGroupId(), artifact.getArtifactId(), type,
                extension, classifier, artifact.getVersion(),
                emptyDefault(artifact.getBaseVersion(),
                        artifact.getVersion()));
    }

    static String typeOf(final Artifact artifact) {
        return artifact.getProperty(
                org.eclipse.aether.artifact.ArtifactProperties.TYPE,
                artifact.getExtension());
    }

    private static String emptyIfNull(final String value) {
        return value == null ? "" : value;
    }

    private static String emptyDefault(
            final String value,
            final String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    String identity() {
        return groupId + ":" + artifactId + ":" + type + ":"
                + classifier + ":" + baseVersion;
    }

    String analysisKey() {
        return groupId + ":" + artifactId + ":" + type
                + (classifier.isEmpty() ? "" : ":" + classifier);
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getType() {
        return type;
    }

    String getExtension() {
        return extension;
    }

    String getClassifier() {
        return classifier;
    }

    String getVersion() {
        return version;
    }

    String getBaseVersion() {
        return baseVersion;
    }
}
