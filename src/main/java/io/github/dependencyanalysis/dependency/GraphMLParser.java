package io.github.dependencyanalysis.dependency;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a GraphML file produced by
 * maven-dependency-plugin into a
 * ModuleDependencyTree.
 */
public final class GraphMLParser {

    /** GraphML namespace URI. */
    private static final String NS_GRAPHML =
            "http://graphml.graphdrawing"
                    + ".org/xmlns";

    /** yFiles namespace URI. */
    private static final String NS_YFILES =
            "http://www.yworks.com"
                    + "/xml/graphml";

    /** Node label element name. */
    private static final String NODE_LABEL =
            "NodeLabel";

    /** Edge label element name. */
    private static final String EDGE_LABEL =
            "EdgeLabel";

    /** Source attribute name. */
    private static final String ATTR_SOURCE =
            "source";

    /** Target attribute name. */
    private static final String ATTR_TARGET =
            "target";

    /** Id attribute name. */
    private static final String ATTR_ID =
            "id";

    /** Minimum segments for scope in label. */
    private static final int SCOPE_SEGMENTS =
            5;

    /** Private constructor. */
    private GraphMLParser() {
    }

    /**
     * Parses a GraphML file into a module
     * dependency tree.
     *
     * @param graphmlFile path to GraphML
     * @param reactorMods reactor module
     *                    coordinates
     * @return parsed module dependency
     *         tree
     * @throws DependencyAnalysisException
     *  if parsing fails
     */
    public static ModuleDependencyTree
            parse(
            final Path graphmlFile,
            final Set<ArtifactCoord>
                    reactorMods)
            throws DependencyAnalysisException {
        final Document doc =
                parseDocument(graphmlFile);
        final Map<String, String>
                nodeLabels =
                extractNodeLabels(doc);
        final List<String[]> edges =
                extractEdges(doc);
        final String rootId =
                findRootId(
                        nodeLabels.keySet(),
                        edges);
        if (rootId == null) {
            throw new DependencyAnalysisException(
                    "No root node found in "
                            + graphmlFile);
        }
        final String rootLabel =
                nodeLabels.get(rootId);
        if (rootLabel == null) {
            throw new DependencyAnalysisException(
                    "Root node label missing "
                            + "in " + graphmlFile);
        }
        final ArtifactCoord moduleCoord =
                ArtifactCoord.parse(rootLabel);
        final Map<String, List<String>>
                adj = buildAdjacency(edges);
        final Set<ArtifactCoord> reactor =
                reactorMods != null
                        ? reactorMods
                        : Set.of();
        final List<DependencyNode> deps =
                buildChildren(
                        rootId, adj,
                        nodeLabels, reactor);
        return new ModuleDependencyTree(
                moduleCoord,
                graphmlFile.getParent(),
                deps);
    }

    /**
     * Parses the XML document from file.
     *
     * @param file GraphML file
     * @return parsed document
     * @throws DependencyAnalysisException
     *  if XML parsing fails
     */
    private static Document parseDocument(
            final Path file)
            throws DependencyAnalysisException {
        try {
            final DocumentBuilderFactory
                    factory =
                    DocumentBuilderFactory
                            .newInstance();
            factory.setNamespaceAware(true);
            final DocumentBuilder builder =
                    factory.newDocumentBuilder();
            return builder.parse(
                    file.toFile());
        } catch (ParserConfigurationException
                | SAXException | IOException
                ex) {
            throw new DependencyAnalysisException(
                    "Failed to parse GraphML: "
                            + file, ex);
        }
    }

    /**
     * Extracts node id to label mappings.
     *
     * @param doc XML document
     * @return map of node id to label
     */
    private static Map<String, String>
            extractNodeLabels(final Document doc) {
        final Map<String, String> result =
                new LinkedHashMap<>();
        final NodeList nodes =
                doc.getElementsByTagNameNS(
                        NS_GRAPHML, "node");
        for (int i = 0; i < nodes.getLength();
                i++) {
            final Element elem =
                    (Element) nodes.item(i);
            final String id =
                    elem.getAttribute(ATTR_ID);
            final String label =
                    findNodeLabel(elem);
            if (id != null && !id.isEmpty()
                    && label != null
                    && !label.isEmpty()) {
                result.put(id, label);
            }
        }
        return result;
    }

    /**
     * Finds the NodeLabel text within a
     * node element.
     *
     * @param nodeElem node element
     * @return label text or null
     */
    private static String findNodeLabel(
            final Element nodeElem) {
        final NodeList labels =
                nodeElem.getElementsByTagNameNS(
                        NS_YFILES,
                        NODE_LABEL);
        if (labels.getLength() > 0) {
            return labels.item(0)
                    .getTextContent()
                    .trim();
        }
        return null;
    }

    /**
     * Extracts edges as source-target
     * pairs.
     *
     * @param doc XML document
     * @return list of edge pairs
     */
    private static List<String[]>
            extractEdges(final Document doc) {
        final List<String[]> result =
                new ArrayList<>();
        final NodeList edges =
                doc.getElementsByTagNameNS(
                        NS_GRAPHML, "edge");
        for (int i = 0; i < edges.getLength();
                i++) {
            final Element elem =
                    (Element) edges.item(i);
            final String src =
                    elem.getAttribute(
                            ATTR_SOURCE);
            final String tgt =
                    elem.getAttribute(
                            ATTR_TARGET);
            if (src != null && !src.isEmpty()
                    && tgt != null
                    && !tgt.isEmpty()) {
                result.add(new String[]{
                        src, tgt});
            }
        }
        return result;
    }

    /**
     * Finds the root node id (the node
     * with no incoming edges).
     *
     * @param nodeIds all node ids
     * @param edges   edge pairs
     * @return root node id or null
     */
    private static String findRootId(
            final Set<String> nodeIds,
            final List<String[]> edges) {
        final Set<String> targets =
                new HashSet<>();
        for (String[] edge : edges) {
            targets.add(edge[1]);
        }
        for (String id : nodeIds) {
            if (!targets.contains(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Builds an adjacency list from edges.
     *
     * @param edges edge pairs
     * @return adjacency map
     */
    private static Map<String, List<String>>
            buildAdjacency(
            final List<String[]> edges) {
        final Map<String, List<String>>
                adj = new HashMap<>();
        for (String[] edge : edges) {
            adj.computeIfAbsent(
                    edge[0],
                    k -> new ArrayList<>())
                    .add(edge[1]);
        }
        return adj;
    }

    /**
     * Builds child dependency nodes
     * recursively from the adjacency list.
     *
     * @param parentId parent node id
     * @param adj      adjacency list
     * @param labels   node labels
     * @param reactor  reactor modules
     * @return child nodes
     */
    private static List<DependencyNode>
            buildChildren(
            final String parentId,
            final Map<String, List<String>>
                    adj,
            final Map<String, String>
                    labels,
            final Set<ArtifactCoord>
                    reactor) {
        final List<String> childIds =
                adj.getOrDefault(
                        parentId,
                        List.of());
        final List<DependencyNode> result =
                new ArrayList<>();
        for (String childId : childIds) {
            final String label =
                    labels.get(childId);
            if (label == null) {
                continue;
            }
            final ArtifactCoord coord =
                    ArtifactCoord.parse(label);
            if (reactor.contains(coord)) {
                continue;
            }
            final String scopeStr =
                    extractScopeFromLabel(label);
            final DependencyScope scope =
                    DependencyScope
                            .fromString(scopeStr);
            if (scope == null) {
                continue;
            }
            final List<DependencyNode>
                    grandChildren =
                    buildChildren(
                            childId, adj,
                            labels, reactor);
            result.add(new DependencyNode(
                    coord, scope,
                    grandChildren));
        }
        return result;
    }

    /**
     * Extracts scope from a label.
     * Returns empty string for
     * 4-segment labels.
     *
     * @param label node label
     * @return scope string
     */
    private static String
            extractScopeFromLabel(
            final String label) {
        final String[] parts =
                label.split(":");
        if (parts.length >= SCOPE_SEGMENTS) {
            return parts[parts.length - 1];
        }
        return "";
    }
}
