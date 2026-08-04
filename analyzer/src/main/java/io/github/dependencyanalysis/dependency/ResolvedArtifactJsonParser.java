package io.github.dependencyanalysis.dependency;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict streaming parser for Artifact Path JSON Schema v2. */
public final class ResolvedArtifactJsonParser {

    /** Supported Schema version. */
    private static final int SCHEMA_VERSION = 2;

    /** Parser with duplicate property detection. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ResolvedArtifactJsonParser() {
    }

    /**
     * Parses and validates one module-local artifact manifest.
     *
     * @param file JSON manifest
     * @return validated manifest
     * @throws DependencyAnalysisException malformed contract
     */
    public static ResolvedArtifactManifest parse(final Path file)
            throws DependencyAnalysisException {
        try (JsonParser json = JSON_FACTORY.createParser(file.toFile())) {
            requireToken(json.nextToken(), JsonToken.START_OBJECT,
                    "document must be an object", file);
            Integer schemaVersion = null;
            List<ArtifactValue> artifactValues = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                requireToken(json.currentToken(), JsonToken.FIELD_NAME,
                        "document property name expected", file);
                final String name = json.currentName();
                final JsonToken value = json.nextToken();
                if ("schemaVersion".equals(name)) {
                    if (value != JsonToken.VALUE_NUMBER_INT) {
                        fail("schemaVersion must be an integer", file);
                    }
                    schemaVersion = json.getIntValue();
                } else if ("artifacts".equals(name)) {
                    artifactValues = parseArtifacts(json, value, file);
                } else {
                    json.skipChildren();
                }
            }
            if (json.nextToken() != null) {
                fail("trailing JSON content is not allowed", file);
            }
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                fail("schemaVersion must equal 2", file);
            }
            if (artifactValues == null) {
                fail("artifacts is required", file);
            }
            return manifest(artifactValues, file);
        } catch (IOException | RuntimeException exception) {
            throw new DependencyAnalysisException(
                    "Invalid resolved artifact JSON: " + file,
                    exception);
        }
    }

    private static List<ArtifactValue> parseArtifacts(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        requireToken(token, JsonToken.START_ARRAY,
                "artifacts must be an array", file);
        final List<ArtifactValue> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            requireToken(json.currentToken(), JsonToken.START_OBJECT,
                    "artifact must be an object", file);
            CoordinateValue coordinates = null;
            String absolutePath = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                requireToken(json.currentToken(), JsonToken.FIELD_NAME,
                        "artifact property name expected", file);
                final String name = json.currentName();
                final JsonToken value = json.nextToken();
                if ("coordinates".equals(name)) {
                    coordinates = parseCoordinates(json, value, file);
                } else if ("absolutePath".equals(name)) {
                    absolutePath = stringValue(json, value,
                            "artifact.absolutePath", file, false);
                } else {
                    json.skipChildren();
                }
            }
            if (coordinates == null || absolutePath == null) {
                fail("artifact coordinates and absolutePath"
                        + " are required", file);
            }
            result.add(new ArtifactValue(
                    coordinates, absolutePath));
        }
        return result;
    }

    private static CoordinateValue parseCoordinates(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        requireToken(token, JsonToken.START_OBJECT,
                "coordinates must be an object", file);
        String groupId = null;
        String artifactId = null;
        String type = null;
        String extension = null;
        String classifier = null;
        String version = null;
        String baseVersion = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            requireToken(json.currentToken(), JsonToken.FIELD_NAME,
                    "coordinate property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            if ("groupId".equals(name)) {
                groupId = stringValue(json, value, name, file, false);
            } else if ("artifactId".equals(name)) {
                artifactId = stringValue(json, value, name, file, false);
            } else if ("type".equals(name)) {
                type = stringValue(json, value, name, file, false);
            } else if ("extension".equals(name)) {
                extension = stringValue(json, value, name, file, false);
            } else if ("classifier".equals(name)) {
                classifier = stringValue(json, value, name, file, true);
            } else if ("version".equals(name)) {
                version = stringValue(json, value, name, file, false);
            } else if ("baseVersion".equals(name)) {
                baseVersion = stringValue(json, value, name, file, false);
            } else {
                json.skipChildren();
            }
        }
        if (groupId == null || artifactId == null || type == null
                || extension == null || classifier == null
                || version == null || baseVersion == null) {
            fail("all coordinate fields are required", file);
        }
        return new CoordinateValue(groupId, artifactId, type,
                extension, classifier, version, baseVersion);
    }

    private static ResolvedArtifactManifest manifest(
            final List<ArtifactValue> values,
            final Path file) throws DependencyAnalysisException {
        final Path manifestDirectory = manifestDirectory(file);
        final List<ResolvedArtifact> artifacts = new ArrayList<>();
        final Set<String> bindings = new HashSet<>();
        for (ArtifactValue value : values) {
            final ArtifactCoord coordinates =
                    value.getCoordinates().toArtifactCoord();
            final String binding = coordinates.toString();
            if (!bindings.add(binding)) {
                fail("duplicate artifact binding: " + binding, file);
            }
            artifacts.add(new ResolvedArtifact(coordinates,
                    existingPath(value.getAbsolutePath(), false, file)));
        }
        artifacts.sort(Comparator
                .comparing((ResolvedArtifact value) ->
                        value.getArtifact().toString())
                .thenComparing(value -> value.getPath().toString()));
        return new ResolvedArtifactManifest(manifestDirectory, artifacts);
    }

    private static Path manifestDirectory(final Path file)
            throws DependencyAnalysisException {
        final Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            fail("manifest parent directory is unavailable", file);
        }
        try {
            return parent.toRealPath();
        } catch (IOException exception) {
            throw new DependencyAnalysisException(
                    "Unable to canonicalize manifest directory: " + parent,
                    exception);
        }
    }

    private static Path existingPath(
            final String raw,
            final boolean directory,
            final Path file) throws DependencyAnalysisException {
        final Path path;
        try {
            path = Path.of(raw);
        } catch (RuntimeException exception) {
            throw new DependencyAnalysisException(
                    "Invalid path in resolved artifact JSON: " + raw,
                    exception);
        }
        if (!path.isAbsolute()) {
            fail("path must be absolute: " + raw, file);
        }
        if (directory ? !Files.isDirectory(path) : !Files.isRegularFile(path)) {
            fail("path does not exist with expected type: " + raw, file);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new DependencyAnalysisException(
                    "Unable to canonicalize path: " + raw, exception);
        }
    }

    private static String stringValue(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final Path file,
            final boolean allowEmpty) throws IOException,
            DependencyAnalysisException {
        if (token != JsonToken.VALUE_STRING) {
            fail(name + " must be a string", file);
        }
        final String value = json.getText();
        if (!allowEmpty && value.isEmpty()) {
            fail(name + " must not be empty", file);
        }
        return value;
    }

    private static void requireToken(
            final JsonToken actual,
            final JsonToken expected,
            final String message,
            final Path file) throws DependencyAnalysisException {
        if (actual != expected) {
            fail(message, file);
        }
    }

    private static void fail(
            final String message,
            final Path file) throws DependencyAnalysisException {
        throw new DependencyAnalysisException(
                message + " in " + file);
    }

    /** Parsed coordinate value retaining all Schema fields. */
    private static final class CoordinateValue {

        /** Group identifier. */
        private final String groupId;

        /** Artifact identifier. */
        private final String artifactId;

        /** Maven type. */
        private final String type;

        /** Physical extension. */
        private final String extension;

        /** Classifier. */
        private final String classifier;

        /** Resolved version. */
        private final String version;

        /** Base version used for GraphML binding. */
        private final String baseVersion;

        CoordinateValue(
                final String group,
                final String artifact,
                final String artifactType,
                final String artifactExtension,
                final String artifactClassifier,
                final String artifactVersion,
                final String artifactBaseVersion) {
            groupId = group;
            artifactId = artifact;
            type = artifactType;
            extension = artifactExtension;
            classifier = artifactClassifier;
            version = artifactVersion;
            baseVersion = artifactBaseVersion;
        }

        ArtifactCoord toArtifactCoord() {
            return new ArtifactCoord(groupId, artifactId, type,
                    baseVersion, classifier);
        }
    }

    /** Parsed external artifact object. */
    private static final class ArtifactValue {

        /** Artifact coordinates. */
        private final CoordinateValue coordinates;

        /** Absolute file path string. */
        private final String absolutePath;

        ArtifactValue(
                final CoordinateValue artifactCoordinates,
                final String path) {
            coordinates = artifactCoordinates;
            absolutePath = path;
        }

        CoordinateValue getCoordinates() {
            return coordinates;
        }

        String getAbsolutePath() {
            return absolutePath;
        }
    }
}
