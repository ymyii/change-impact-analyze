package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// - Classpath Evidence Schema v1
/** Strict streaming parser for Classpath Evidence Schema v1. */
public final class ClasspathEvidenceJsonParser {

    /** Supported Schema version. */
    private static final int SCHEMA_VERSION = 1;

    /** Retained Maven scopes. */
    private static final Set<String> SCOPES = Set.of(
            "compile", "runtime", "provided", "test", "system");

    /** Duplicate-aware JSON factory. */
    private static final JsonFactory JSON = new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ClasspathEvidenceJsonParser() {
    }

    /**
     * Parses and validates one complete evidence document.
     *
     * @param file evidence JSON
     * @return validated Module evidence
     * @throws IOException malformed evidence or invalid paths
     */
    public static ModuleClasspathEvidence parse(final Path file)
            throws IOException {
        try (JsonParser json = JSON.createParser(file.toFile())) {
            require(json.nextToken(), JsonToken.START_OBJECT,
                    "document must be an object", file);
            Integer schema = null;
            Integer javaMajor = null;
            ArtifactCoord module = null;
            Path moduleDirectory = null;
            List<ClasspathEvidenceEntry> entries = null;
            List<String> issues = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                require(json.currentToken(), JsonToken.FIELD_NAME,
                        "property name expected", file);
                final String name = json.currentName();
                final JsonToken token = json.nextToken();
                switch (name) {
                    case "schemaVersion" -> {
                        schema = integer(json, token, name, file);
                    }
                    case "javaMajor" -> {
                        javaMajor = integer(json, token, name, file);
                    }
                    case "module" -> {
                        module = coordinate(json, token, file);
                    }
                    case "moduleDirectory" -> {
                        moduleDirectory = canonical(string(
                                json, token, name, file), true, file);
                    }
                    case "entries" -> {
                        entries = entries(json, token, file);
                    }
                    case "issues" -> {
                        issues = strings(json, token, file);
                    }
                    default -> fail("unknown property: " + name, file);
                }
            }
            if (json.nextToken() != null) {
                fail("trailing JSON content", file);
            }
            if (schema == null || schema != SCHEMA_VERSION) {
                fail("schemaVersion must equal 1", file);
            }
            if (javaMajor == null || module == null
                    || moduleDirectory == null || entries == null
                    || issues == null) {
                fail("all fields are required", file);
            }
            return new ModuleClasspathEvidence(javaMajor, module,
                    moduleDirectory, entries, issues);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid classpath evidence: " + file,
                    exception);
        }
    }

    private static List<ClasspathEvidenceEntry> entries(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException {
        require(token, JsonToken.START_ARRAY,
                "entries must be an array", file);
        final List<ClasspathEvidenceEntry> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            result.add(entry(json, json.currentToken(), result.size(), file));
        }
        return List.copyOf(result);
    }

    private static ClasspathEvidenceEntry entry(
            final JsonParser json,
            final JsonToken token,
            final int expectedOrder,
            final Path file) throws IOException {
        require(token, JsonToken.START_OBJECT,
                "entry must be an object", file);
        Integer order = null;
        String origin = null;
        ArtifactCoord coordinate = null;
        String scope = null;
        Path path = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            require(json.currentToken(), JsonToken.FIELD_NAME,
                    "entry property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            switch (name) {
                case "order" -> {
                    order = integer(json, value, name, file);
                }
                case "origin" -> {
                    origin = string(json, value, name, file);
                }
                case "coordinates" -> {
                    coordinate = coordinate(json, value, file);
                }
                case "scope" -> {
                    scope = string(json, value, name, true, file);
                }
                case "absolutePath" -> {
                    path = canonical(string(json, value, name, file),
                            false, file);
                }
                default -> fail("unknown entry property: " + name, file);
            }
        }
        if (order == null || origin == null || coordinate == null
                || scope == null || path == null) {
            fail("all entry fields are required", file);
        }
        if (order != expectedOrder) {
            fail("classpath order must be contiguous", file);
        }
        final CodeOrigin parsedOrigin;
        try {
            parsedOrigin = CodeOrigin.valueOf(origin);
        } catch (IllegalArgumentException exception) {
            fail("invalid source kind: " + origin, file);
            return null;
        }
        if (parsedOrigin != CodeOrigin.PROJECT
                && parsedOrigin != CodeOrigin.REACTOR_DEPENDENCY
                && parsedOrigin != CodeOrigin.DEPENDENCY) {
            fail("unsupported source kind: " + origin, file);
        }
        if (parsedOrigin == CodeOrigin.PROJECT) {
            if (!scope.isEmpty() || !Files.isDirectory(path)) {
                fail("PROJECT requires empty scope and directory", file);
            }
        } else if (!SCOPES.contains(scope)) {
            fail("invalid dependency scope: " + scope, file);
        } else if (parsedOrigin == CodeOrigin.REACTOR_DEPENDENCY
                && !Files.isDirectory(path)) {
            fail("REACTOR_DEPENDENCY requires directory", file);
        } else if (parsedOrigin == CodeOrigin.DEPENDENCY
                && !Files.isRegularFile(path)) {
            fail("DEPENDENCY requires regular file", file);
        }
        return new ClasspathEvidenceEntry(order, parsedOrigin,
                coordinate, scope, path);
    }

    private static ArtifactCoord coordinate(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException {
        require(token, JsonToken.START_OBJECT,
                "coordinates must be an object", file);
        String group = null;
        String artifact = null;
        String type = null;
        String extension = null;
        String classifier = null;
        String version = null;
        String baseVersion = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            require(json.currentToken(), JsonToken.FIELD_NAME,
                    "coordinate property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            final String parsed = string(json, value, name,
                    "classifier".equals(name), file);
            switch (name) {
                case "groupId" -> {
                    group = parsed;
                }
                case "artifactId" -> {
                    artifact = parsed;
                }
                case "type" -> {
                    type = parsed;
                }
                case "extension" -> {
                    extension = parsed;
                }
                case "classifier" -> {
                    classifier = parsed;
                }
                case "version" -> {
                    version = parsed;
                }
                case "baseVersion" -> {
                    baseVersion = parsed;
                }
                default -> fail("unknown coordinate property: " + name,
                        file);
            }
        }
        if (group == null || artifact == null || type == null
                || extension == null || classifier == null
                || version == null || baseVersion == null) {
            fail("all coordinate fields are required", file);
        }
        return new ArtifactCoord(group, artifact, type, version, classifier);
    }

    private static List<String> strings(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException {
        require(token, JsonToken.START_ARRAY,
                "issues must be an array", file);
        final List<String> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            result.add(string(json, json.currentToken(), "issue", file));
        }
        return List.copyOf(result);
    }

    private static Path canonical(
            final String value,
            final boolean directory,
            final Path file) throws IOException {
        final Path path = Path.of(value);
        if (!path.isAbsolute()) {
            fail("path must be absolute: " + value, file);
        }
        final Path real = path.toRealPath();
        if (!real.equals(path.normalize())) {
            fail("path must be canonical: " + value, file);
        }
        if (directory && !Files.isDirectory(real)) {
            fail("path must be a directory: " + value, file);
        }
        return real;
    }

    private static Integer integer(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final Path file) throws IOException {
        require(token, JsonToken.VALUE_NUMBER_INT,
                name + " must be an integer", file);
        return json.getIntValue();
    }

    private static String string(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final Path file) throws IOException {
        return string(json, token, name, false, file);
    }

    private static String string(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final boolean allowEmpty,
            final Path file) throws IOException {
        require(token, JsonToken.VALUE_STRING,
                name + " must be a string", file);
        final String value = json.getText();
        if (!allowEmpty && value.isBlank()) {
            fail(name + " must not be blank", file);
        }
        return value;
    }

    private static void require(
            final JsonToken actual,
            final JsonToken expected,
            final String reason,
            final Path file) throws IOException {
        if (actual != expected) {
            fail(reason, file);
        }
    }

    private static void fail(
            final String reason,
            final Path file) throws IOException {
        throw new IOException("Invalid Classpath Evidence Schema v1: "
                + reason + "; file=" + file.getFileName());
    }
}
