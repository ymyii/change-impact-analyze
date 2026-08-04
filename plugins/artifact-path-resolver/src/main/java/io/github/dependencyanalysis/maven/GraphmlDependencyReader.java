package io.github.dependencyanalysis.maven;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the selected dependency bindings from Dependency Plugin GraphML. */
final class GraphmlDependencyReader {

    /** GraphML namespace. */
    private static final String GRAPHML_NAMESPACE =
            "http://graphml.graphdrawing.org/xmlns";

    /** yFiles GraphML namespace. */
    private static final String YFILES_NAMESPACE =
            "http://www.yworks.com/xml/graphml";

    /** Scopes exposed to Analyzer. */
    private static final Set<String> INCLUDED_SCOPES;

    /** Maven dependency scopes accepted in GraphML. */
    private static final Set<String> KNOWN_SCOPES;

    /** Module coordinate segment minimum. */
    private static final int MODULE_MINIMUM_SEGMENTS = 4;

    /** Module coordinate segment maximum. */
    private static final int MODULE_MAXIMUM_SEGMENTS = 5;

    /** Dependency coordinate segment minimum. */
    private static final int DEPENDENCY_MINIMUM_SEGMENTS = 5;

    /** Dependency coordinate segment maximum. */
    private static final int DEPENDENCY_MAXIMUM_SEGMENTS = 6;

    /** Version index without classifier. */
    private static final int VERSION_INDEX = 3;

    /** Classifier index. */
    private static final int CLASSIFIER_INDEX = 3;

    /** Version index with classifier. */
    private static final int CLASSIFIED_VERSION_INDEX = 4;

    static {
        final Set<String> scopes = new LinkedHashSet<String>();
        scopes.add("compile");
        scopes.add("runtime");
        scopes.add("provided");
        scopes.add("system");
        INCLUDED_SCOPES = Collections.unmodifiableSet(scopes);
        final Set<String> knownScopes = new LinkedHashSet<String>(scopes);
        knownScopes.add("test");
        KNOWN_SCOPES = Collections.unmodifiableSet(knownScopes);
    }

    private GraphmlDependencyReader() {
    }

    /**
     * Reads one module graph.
     *
     * @param file module-local GraphML file
     * @return validated selected dependency graph
     * @throws GraphmlReadException if the graph is unsafe or malformed
     */
    static Graph read(final Path file) throws GraphmlReadException {
        final Document document = parseDocument(file);
        validateRootElement(document, file);
        final Map<String, String> labels = extractNodeLabels(document, file);
        final List<Edge> edges = extractEdges(document, labels, file);
        final String root = findSingleRoot(labels.keySet(), edges, file);
        final Map<String, List<String>> adjacency = adjacency(edges);
        final VisitState visitState = new VisitState();
        final List<Dependency> dependencies =
                new ArrayList<Dependency>();
        final Coordinate module = Coordinate.parseModule(labels.get(root));
        visit(root, true, adjacency, labels, visitState,
                dependencies, file);
        if (visitState.getVisited().size() != labels.size()) {
            throw new GraphmlReadException(
                    "GraphML contains nodes unreachable from its root: "
                            + file);
        }
        return new Graph(module, dependencies);
    }

    private static Document parseDocument(final Path file)
            throws GraphmlReadException {
        try {
            final DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/"
                            + "load-external-dtd",
                    false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(file.toFile());
        } catch (ParserConfigurationException | SAXException
                 | IOException | RuntimeException exception) {
            throw new GraphmlReadException(
                    "Unable to parse dependency GraphML: " + file,
                    exception);
        }
    }

    private static void validateRootElement(
            final Document document,
            final Path file) throws GraphmlReadException {
        final Element root = document.getDocumentElement();
        if (root == null
                || !GRAPHML_NAMESPACE.equals(root.getNamespaceURI())
                || !"graphml".equals(root.getLocalName())) {
            throw new GraphmlReadException(
                    "Dependency graph is not GraphML: " + file);
        }
    }

    private static Map<String, String> extractNodeLabels(
            final Document document,
            final Path file) throws GraphmlReadException {
        final Map<String, String> labels =
                new LinkedHashMap<String, String>();
        final NodeList nodes = document.getElementsByTagNameNS(
                GRAPHML_NAMESPACE, "node");
        if (nodes.getLength() == 0) {
            throw new GraphmlReadException(
                    "Dependency GraphML contains no nodes: " + file);
        }
        for (int index = 0; index < nodes.getLength(); index++) {
            final Element node = (Element) nodes.item(index);
            final String id = node.getAttribute("id");
            final NodeList nodeLabels = node.getElementsByTagNameNS(
                    YFILES_NAMESPACE, "NodeLabel");
            if (id == null || id.isEmpty() || nodeLabels.getLength() != 1) {
                throw new GraphmlReadException(
                        "GraphML node must have an id and one NodeLabel: "
                                + file);
            }
            final String label = nodeLabels.item(0).getTextContent().trim();
            if (label.isEmpty() || labels.put(id, label) != null) {
                throw new GraphmlReadException(
                        "GraphML contains an empty label or duplicate node id: "
                                + id);
            }
        }
        return labels;
    }

    private static List<Edge> extractEdges(
            final Document document,
            final Map<String, String> labels,
            final Path file) throws GraphmlReadException {
        final List<Edge> edges = new ArrayList<Edge>();
        final NodeList elements = document.getElementsByTagNameNS(
                GRAPHML_NAMESPACE, "edge");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            final String source = element.getAttribute("source");
            final String target = element.getAttribute("target");
            if (!labels.containsKey(source) || !labels.containsKey(target)) {
                throw new GraphmlReadException(
                        "GraphML edge references an unknown node: " + file);
            }
            edges.add(new Edge(source, target));
        }
        return edges;
    }

    private static String findSingleRoot(
            final Set<String> nodeIds,
            final List<Edge> edges,
            final Path file) throws GraphmlReadException {
        final Set<String> targets = new HashSet<String>();
        for (Edge edge : edges) {
            targets.add(edge.getTarget());
        }
        String root = null;
        for (String nodeId : nodeIds) {
            if (!targets.contains(nodeId)) {
                if (root != null) {
                    throw new GraphmlReadException(
                            "GraphML must contain exactly one root: " + file);
                }
                root = nodeId;
            }
        }
        if (root == null) {
            throw new GraphmlReadException(
                    "GraphML must contain exactly one root: " + file);
        }
        return root;
    }

    private static Map<String, List<String>> adjacency(
            final List<Edge> edges) {
        final Map<String, List<String>> result =
                new HashMap<String, List<String>>();
        for (Edge edge : edges) {
            List<String> targets = result.get(edge.getSource());
            if (targets == null) {
                targets = new ArrayList<String>();
                result.put(edge.getSource(), targets);
            }
            targets.add(edge.getTarget());
        }
        return result;
    }

    private static void visit(
            final String nodeId,
            final boolean selectedBranch,
            final Map<String, List<String>> adjacency,
            final Map<String, String> labels,
            final VisitState visitState,
            final List<Dependency> dependencies,
            final Path file) throws GraphmlReadException {
        if (!visitState.getActive().add(nodeId)) {
            throw new GraphmlReadException(
                    "GraphML dependency graph contains a cycle: " + file);
        }
        if (!visitState.getVisited().add(nodeId)) {
            throw new GraphmlReadException(
                    "GraphML dependency node is reachable more than once: "
                            + labels.get(nodeId));
        }
        final List<String> children = adjacency.get(nodeId);
        if (children != null) {
            for (String child : children) {
                final Dependency dependency =
                        Dependency.parse(labels.get(child));
                final boolean selected = selectedBranch
                        && INCLUDED_SCOPES.contains(dependency.getScope());
                if (selected) {
                    dependencies.add(dependency);
                }
                visit(child, selected, adjacency, labels, visitState,
                        dependencies, file);
            }
        }
        visitState.getActive().remove(nodeId);
    }

    /** Validated module dependency graph. */
    static final class Graph {

        /** Module coordinates. */
        private final Coordinate module;

        /** Selected dependencies. */
        private final List<Dependency> dependencies;

        Graph(final Coordinate moduleCoordinate,
              final List<Dependency> selectedDependencies) {
            module = moduleCoordinate;
            dependencies = Collections.unmodifiableList(
                    selectedDependencies);
        }

        Coordinate getModule() {
            return module;
        }

        List<Dependency> getDependencies() {
            return dependencies;
        }
    }

    /** Parsed artifact coordinates as emitted by Dependency Plugin. */
    static final class Coordinate {

        /** Group identifier. */
        private final String groupId;

        /** Artifact identifier. */
        private final String artifactId;

        /** Maven artifact type. */
        private final String type;

        /** Classifier, possibly empty. */
        private final String classifier;

        /** Version or base version from GraphML. */
        private final String version;

        Coordinate(final String group,
                   final String artifact,
                   final String artifactType,
                   final String artifactClassifier,
                   final String artifactVersion) {
            groupId = group;
            artifactId = artifact;
            type = artifactType;
            classifier = artifactClassifier;
            version = artifactVersion;
        }

        static Coordinate parseModule(final String label)
                throws GraphmlReadException {
            return parse(label, false);
        }

        static Coordinate parseDependency(final String label)
                throws GraphmlReadException {
            return parse(label, true);
        }

        private static Coordinate parse(
                final String label,
                final boolean dependency) throws GraphmlReadException {
            if (label == null) {
                throw new GraphmlReadException(
                        "GraphML node label is missing");
            }
            final String[] parts = label.trim().split(":", -1);
            final int minimum = dependency
                    ? DEPENDENCY_MINIMUM_SEGMENTS
                    : MODULE_MINIMUM_SEGMENTS;
            final int maximum = dependency
                    ? DEPENDENCY_MAXIMUM_SEGMENTS
                    : MODULE_MAXIMUM_SEGMENTS;
            if (parts.length < minimum || parts.length > maximum) {
                throw new GraphmlReadException(
                        "Invalid GraphML artifact label: " + label);
            }
            for (String part : parts) {
                if (part.isEmpty()) {
                    throw new GraphmlReadException(
                            "Invalid GraphML artifact label: " + label);
                }
            }
            final boolean classifier = parts.length == maximum;
            final int versionIndex = classifier
                    ? CLASSIFIED_VERSION_INDEX : VERSION_INDEX;
            return new Coordinate(parts[0], parts[1], parts[2],
                    classifier ? parts[CLASSIFIER_INDEX] : "",
                    parts[versionIndex]);
        }

        String identity() {
            return groupId + ":" + artifactId + ":" + type + ":"
                    + classifier + ":" + version;
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

        String getClassifier() {
            return classifier;
        }

        String getVersion() {
            return version;
        }
    }

    /** Parsed dependency coordinate and authoritative GraphML scope. */
    static final class Dependency {

        /** Coordinates. */
        private final Coordinate coordinate;

        /** Effective selected scope. */
        private final String scope;

        Dependency(final Coordinate artifactCoordinate,
                   final String dependencyScope) {
            coordinate = artifactCoordinate;
            scope = dependencyScope;
        }

        static Dependency parse(final String label)
                throws GraphmlReadException {
            final String[] parts = label == null
                    ? new String[0] : label.trim().split(":", -1);
            if (parts.length < DEPENDENCY_MINIMUM_SEGMENTS
                    || parts.length > DEPENDENCY_MAXIMUM_SEGMENTS) {
                throw new GraphmlReadException(
                        "Invalid GraphML dependency label: " + label);
            }
            final String scope = parts[parts.length - 1];
            if (!KNOWN_SCOPES.contains(scope)) {
                throw new GraphmlReadException(
                        "Unknown GraphML dependency scope: " + scope);
            }
            return new Dependency(Coordinate.parseDependency(label), scope);
        }

        Coordinate getCoordinate() {
            return coordinate;
        }

        String getScope() {
            return scope;
        }
    }

    /** Mutable DFS bookkeeping. */
    private static final class VisitState {

        /** Nodes visited from the root. */
        private final Set<String> visited = new HashSet<String>();

        /** Nodes on the current DFS path. */
        private final Set<String> active = new HashSet<String>();

        Set<String> getVisited() {
            return visited;
        }

        Set<String> getActive() {
            return active;
        }
    }

    /** Directed GraphML edge. */
    private static final class Edge {

        /** Source node id. */
        private final String source;

        /** Target node id. */
        private final String target;

        Edge(final String sourceNode, final String targetNode) {
            source = sourceNode;
            target = targetNode;
        }

        String getSource() {
            return source;
        }

        String getTarget() {
            return target;
        }
    }

    /** Checked failure for unsafe or invalid dependency GraphML. */
    static final class GraphmlReadException extends Exception {

        /** Serialization identifier. */
        private static final long serialVersionUID = 1L;

        GraphmlReadException(final String message) {
            super(message);
        }

        GraphmlReadException(
                final String message,
                final Throwable cause) {
            super(message, cause);
        }
    }
}
