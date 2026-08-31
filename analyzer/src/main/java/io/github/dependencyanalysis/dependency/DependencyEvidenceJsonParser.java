package io.github.dependencyanalysis.dependency;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict streaming parser for Dependency Evidence Schema v3. */
public final class DependencyEvidenceJsonParser {

    /** Supported Schema version. */
    private static final int SCHEMA_VERSION = 3;

    /** Duplicate-aware JSON factory. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private DependencyEvidenceJsonParser() {
    }

    /**
     * Parses one complete Module evidence document.
     *
     * @param file evidence JSON
     * @return validated Module evidence
     * @throws DependencyAnalysisException malformed evidence
     */
    public static ModuleDependencyEvidence parse(final Path file)
            throws DependencyAnalysisException {
        try (JsonParser json = JSON_FACTORY.createParser(file.toFile())) {
            require(json.nextToken(), JsonToken.START_OBJECT,
                    "document must be an object", file);
            Integer schemaVersion = null;
            Coordinate module = null;
            String moduleDirectory = null;
            List<DependencyNode> dependencies = null;
            GraphValue graph = null;
            List<String> reactorKeys = null;
            List<ResolvedArtifact> artifacts = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                require(json.currentToken(), JsonToken.FIELD_NAME,
                        "document property name expected", file);
                final String name = json.currentName();
                final JsonToken value = json.nextToken();
                if ("schemaVersion".equals(name)) {
                    require(value, JsonToken.VALUE_NUMBER_INT,
                            "schemaVersion must be an integer", file);
                    schemaVersion = json.getIntValue();
                } else if ("module".equals(name)) {
                    module = coordinates(json, value, file);
                } else if ("moduleDirectory".equals(name)) {
                    moduleDirectory = string(json, value,
                            "moduleDirectory", false, file);
                } else if ("dependencies".equals(name)) {
                    dependencies = dependencies(json, value, file);
                } else if ("occurrenceGraph".equals(name)) {
                    graph = graph(json, value, file);
                } else if ("selectedReactorKeys".equals(name)) {
                    reactorKeys = strings(json, value,
                            "selectedReactorKeys", file);
                } else if ("artifacts".equals(name)) {
                    artifacts = artifacts(json, value, file);
                } else {
                    fail("unknown document property: " + name, file);
                }
            }
            if (json.nextToken() != null) {
                fail("trailing JSON content is not allowed", file);
            }
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                fail("schemaVersion must equal 3", file);
            }
            if (module == null || moduleDirectory == null
                    || dependencies == null || graph == null
                    || reactorKeys == null || artifacts == null) {
                fail("all dependency evidence fields are required", file);
            }
            return evidence(module, moduleDirectory, dependencies,
                    graph, reactorKeys, artifacts, file);
        } catch (DependencyAnalysisException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DependencyAnalysisException(
                    "Invalid dependency evidence JSON: " + file,
                    exception);
        }
    }

    private static ModuleDependencyEvidence evidence(
            final Coordinate module,
            final String moduleDirectory,
            final List<DependencyNode> dependencies,
            final GraphValue graph,
            final List<String> reactorKeys,
            final List<ResolvedArtifact> artifacts,
            final Path file) throws DependencyAnalysisException {
        final Path directory = existingPath(
                moduleDirectory, true, file);
        final ArtifactCoord moduleCoordinate = module.toArtifactCoord();
        final ModuleDependencyOccurrenceGraph occurrenceGraph =
                new ModuleDependencyOccurrenceGraph(
                        graph.rootId, graph.occurrences, graph.edges);
        final ModuleDependencyOccurrenceGraph.Validation validation =
                occurrenceGraph.validate();
        if (!validation.valid()) {
            fail("invalid occurrence graph: " + validation.reason(), file);
        }
        if (!occurrenceGraph.root().artifact().equals(moduleCoordinate)) {
            fail("occurrence root does not match module", file);
        }
        final Set<String> reactorSet = new LinkedHashSet<>(reactorKeys);
        if (reactorSet.size() != reactorKeys.size()) {
            fail("selectedReactorKeys contains duplicates", file);
        }
        final Set<ArtifactCoord> selected = new LinkedHashSet<>();
        collect(dependencies, selected);
        final Set<ArtifactCoord> bound = new LinkedHashSet<>();
        for (ResolvedArtifact artifact : artifacts) {
            if (!bound.add(artifact.getArtifact())) {
                fail("duplicate artifact binding: "
                        + artifact.getArtifact(), file);
            }
        }
        if (!selected.equals(bound)) {
            final Set<ArtifactCoord> missing = new LinkedHashSet<>(selected);
            missing.removeAll(bound);
            final Set<ArtifactCoord> unexpected = new LinkedHashSet<>(bound);
            unexpected.removeAll(selected);
            fail("selected dependency bindings differ: missing=" + missing
                    + "; unexpected=" + unexpected, file);
        }
        return new ModuleDependencyEvidence(moduleCoordinate, directory,
                dependencies, occurrenceGraph, reactorKeys, artifacts);
    }

    private static void collect(
            final List<DependencyNode> dependencies,
            final Set<ArtifactCoord> result) {
        for (DependencyNode dependency : dependencies) {
            result.add(dependency.getArtifact());
            collect(dependency.getChildren(), result);
        }
    }

    private static List<DependencyNode> dependencies(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_ARRAY,
                "dependencies must be an array", file);
        final List<DependencyNode> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            result.add(dependency(json, json.currentToken(), file));
        }
        return List.copyOf(result);
    }

    private static DependencyNode dependency(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_OBJECT,
                "dependency must be an object", file);
        Coordinate coordinates = null;
        String scope = null;
        List<DependencyNode> children = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            require(json.currentToken(), JsonToken.FIELD_NAME,
                    "dependency property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            if ("coordinates".equals(name)) {
                coordinates = coordinates(json, value, file);
            } else if ("scope".equals(name)) {
                scope = string(json, value, "dependency.scope",
                        false, file);
            } else if ("children".equals(name)) {
                children = dependencies(json, value, file);
            } else {
                fail("unknown dependency property: " + name, file);
            }
        }
        if (coordinates == null || scope == null || children == null) {
            fail("dependency coordinates, scope and children are required",
                    file);
        }
        final DependencyScope parsedScope = DependencyScope.fromString(scope);
        if (parsedScope == null) {
            fail("dependency scope is not retained: " + scope, file);
        }
        return new DependencyNode(
                coordinates.toArtifactCoord(), parsedScope, children);
    }

    private static GraphValue graph(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_OBJECT,
                "occurrenceGraph must be an object", file);
        String rootId = null;
        List<ModuleDependencyOccurrenceGraph.Occurrence> occurrences = null;
        List<ModuleDependencyOccurrenceGraph.Edge> edges = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            require(json.currentToken(), JsonToken.FIELD_NAME,
                    "occurrenceGraph property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            if ("rootId".equals(name)) {
                rootId = string(json, value, "rootId", false, file);
            } else if ("occurrences".equals(name)) {
                occurrences = occurrences(json, value, file);
            } else if ("edges".equals(name)) {
                edges = edges(json, value, file);
            } else {
                fail("unknown occurrenceGraph property: " + name, file);
            }
        }
        if (rootId == null || occurrences == null || edges == null) {
            fail("occurrenceGraph rootId, occurrences and edges are required",
                    file);
        }
        return new GraphValue(rootId, occurrences, edges);
    }

    private static List<ModuleDependencyOccurrenceGraph.Occurrence>
            occurrences(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_ARRAY,
                "occurrences must be an array", file);
        final List<ModuleDependencyOccurrenceGraph.Occurrence> result =
                new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            result.add(occurrence(json, json.currentToken(), file));
        }
        return List.copyOf(result);
    }

    private static ModuleDependencyOccurrenceGraph.Occurrence occurrence(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_OBJECT,
                "occurrence must be an object", file);
        String id = null;
        Coordinate coordinates = null;
        String scope = null;
        Boolean moduleRoot = null;
        Boolean reactor = null;
        while (json.nextToken() != JsonToken.END_OBJECT) {
            require(json.currentToken(), JsonToken.FIELD_NAME,
                    "occurrence property name expected", file);
            final String name = json.currentName();
            final JsonToken value = json.nextToken();
            if ("id".equals(name)) {
                id = string(json, value, "occurrence.id", false, file);
            } else if ("coordinates".equals(name)) {
                coordinates = coordinates(json, value, file);
            } else if ("scope".equals(name)) {
                scope = string(json, value, "occurrence.scope", true, file);
            } else if ("moduleRoot".equals(name)) {
                moduleRoot = bool(json, value, "moduleRoot", file);
            } else if ("reactor".equals(name)) {
                reactor = bool(json, value, "reactor", file);
            } else {
                fail("unknown occurrence property: " + name, file);
            }
        }
        if (id == null || coordinates == null || scope == null
                || moduleRoot == null || reactor == null) {
            fail("all occurrence fields are required", file);
        }
        final DependencyScope parsedScope;
        if (moduleRoot) {
            if (!scope.isEmpty() || reactor) {
                fail("module root scope must be empty and non-reactor", file);
            }
            parsedScope = null;
        } else {
            parsedScope = DependencyScope.fromString(scope);
            if (parsedScope == null) {
                fail("occurrence scope is not retained: " + scope, file);
            }
        }
        return new ModuleDependencyOccurrenceGraph.Occurrence(
                id, coordinates.toArtifactCoord(), parsedScope,
                moduleRoot, reactor);
    }

    private static List<ModuleDependencyOccurrenceGraph.Edge> edges(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_ARRAY,
                "edges must be an array", file);
        final List<ModuleDependencyOccurrenceGraph.Edge> result =
                new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            require(json.currentToken(), JsonToken.START_OBJECT,
                    "edge must be an object", file);
            String parent = null;
            String child = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                require(json.currentToken(), JsonToken.FIELD_NAME,
                        "edge property name expected", file);
                final String name = json.currentName();
                final JsonToken value = json.nextToken();
                if ("parentId".equals(name)) {
                    parent = string(json, value, "parentId", false, file);
                } else if ("childId".equals(name)) {
                    child = string(json, value, "childId", false, file);
                } else {
                    fail("unknown edge property: " + name, file);
                }
            }
            if (parent == null || child == null) {
                fail("edge parentId and childId are required", file);
            }
            result.add(new ModuleDependencyOccurrenceGraph.Edge(
                    parent, child));
        }
        return List.copyOf(result);
    }

    private static List<ResolvedArtifact> artifacts(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_ARRAY,
                "artifacts must be an array", file);
        final List<ResolvedArtifact> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            require(json.currentToken(), JsonToken.START_OBJECT,
                    "artifact must be an object", file);
            Coordinate coordinates = null;
            String absolutePath = null;
            while (json.nextToken() != JsonToken.END_OBJECT) {
                require(json.currentToken(), JsonToken.FIELD_NAME,
                        "artifact property name expected", file);
                final String name = json.currentName();
                final JsonToken value = json.nextToken();
                if ("coordinates".equals(name)) {
                    coordinates = coordinates(json, value, file);
                } else if ("absolutePath".equals(name)) {
                    absolutePath = string(json, value,
                            "absolutePath", false, file);
                } else {
                    fail("unknown artifact property: " + name, file);
                }
            }
            if (coordinates == null || absolutePath == null) {
                fail("artifact coordinates and absolutePath are required",
                        file);
            }
            result.add(new ResolvedArtifact(
                    coordinates.toArtifactCoord(),
                    existingPath(absolutePath, false, file)));
        }
        return List.copyOf(result);
    }

    private static Coordinate coordinates(
            final JsonParser json,
            final JsonToken token,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_OBJECT,
                "coordinates must be an object", file);
        String groupId = null;
        String artifactId = null;
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
            if ("groupId".equals(name)) {
                groupId = string(json, value, name, false, file);
            } else if ("artifactId".equals(name)) {
                artifactId = string(json, value, name, false, file);
            } else if ("type".equals(name)) {
                type = string(json, value, name, false, file);
            } else if ("extension".equals(name)) {
                extension = string(json, value, name, false, file);
            } else if ("classifier".equals(name)) {
                classifier = string(json, value, name, true, file);
            } else if ("version".equals(name)) {
                version = string(json, value, name, false, file);
            } else if ("baseVersion".equals(name)) {
                baseVersion = string(json, value, name, false, file);
            } else {
                fail("unknown coordinate property: " + name, file);
            }
        }
        if (groupId == null || artifactId == null || type == null
                || extension == null || classifier == null
                || version == null || baseVersion == null) {
            fail("all coordinate fields are required", file);
        }
        return new Coordinate(groupId, artifactId, type, extension,
                classifier, version, baseVersion);
    }

    private static List<String> strings(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.START_ARRAY,
                name + " must be an array", file);
        final List<String> result = new ArrayList<>();
        while (json.nextToken() != JsonToken.END_ARRAY) {
            result.add(string(json, json.currentToken(),
                    name + " entry", false, file));
        }
        return List.copyOf(result);
    }

    private static Boolean bool(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final Path file) throws IOException,
            DependencyAnalysisException {
        if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
            fail(name + " must be a boolean", file);
        }
        return json.getBooleanValue();
    }

    private static String string(
            final JsonParser json,
            final JsonToken token,
            final String name,
            final boolean allowEmpty,
            final Path file) throws IOException,
            DependencyAnalysisException {
        require(token, JsonToken.VALUE_STRING,
                name + " must be a string", file);
        final String value = json.getText();
        if (!allowEmpty && value.isEmpty()) {
            fail(name + " must not be empty", file);
        }
        return value;
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
                    "Invalid path in dependency evidence: " + raw,
                    exception);
        }
        if (!path.isAbsolute()) {
            fail("path must be absolute: " + raw, file);
        }
        if (directory ? !Files.isDirectory(path)
                : !Files.isRegularFile(path)) {
            fail("path does not exist with expected type: " + raw, file);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new DependencyAnalysisException(
                    "Unable to canonicalize path: " + raw, exception);
        }
    }

    private static void require(
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
        throw new DependencyAnalysisException(message + " in " + file);
    }

    /** Parsed coordinate object. */
    private static final class Coordinate {

        /** Group id. */
        private final String groupId;

        /** Artifact id. */
        private final String artifactId;

        /** Maven type. */
        private final String type;

        /** Physical extension. */
        @SuppressWarnings("unused")
        private final String extension;

        /** Classifier. */
        private final String classifier;

        /** Resolved version. */
        @SuppressWarnings("unused")
        private final String version;

        /** Logical Maven version. */
        private final String baseVersion;

        Coordinate(
                final String group,
                final String artifact,
                final String artifactType,
                final String artifactExtension,
                final String artifactClassifier,
                final String resolvedVersion,
                final String logicalVersion) {
            groupId = group;
            artifactId = artifact;
            type = artifactType;
            extension = artifactExtension;
            classifier = artifactClassifier;
            version = resolvedVersion;
            baseVersion = logicalVersion;
        }

        ArtifactCoord toArtifactCoord() {
            return new ArtifactCoord(groupId, artifactId, type,
                    baseVersion, classifier);
        }
    }

    /** Parsed occurrence graph fields. */
    private static final class GraphValue {

        /** Root id. */
        private final String rootId;

        /** Occurrences. */
        private final List<ModuleDependencyOccurrenceGraph.Occurrence>
                occurrences;

        /** Edges. */
        private final List<ModuleDependencyOccurrenceGraph.Edge> edges;

        GraphValue(
                final String root,
                final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes,
                final List<ModuleDependencyOccurrenceGraph.Edge> graphEdges) {
            rootId = root;
            occurrences = nodes;
            edges = graphEdges;
        }
    }
}
