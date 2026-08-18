package io.github.dependencyanalysis.tree;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/** Secure minimal Maven POM parser. */
final class SafePomParser {

    /** Explicit active profile ids. */
    private final Set<String> activeProfiles;

    /** Explicit inactive profile ids. */
    private final Set<String> inactiveProfiles;

    /** Maven user properties. */
    private final Map<String, String> properties;

    /**
     * Creates parser from Maven argument tokens.
     *
     * @param mavenArguments validated tokens
     */
    SafePomParser(
            final List<String> mavenArguments) {
        activeProfiles = new HashSet<>();
        inactiveProfiles = new HashSet<>();
        properties = new HashMap<>();
        parseArguments(mavenArguments);
    }

    /**
     * Parses one POM.
     *
     * @param repositoryRoot snapshot root
     * @param relativePom repository-relative POM
     * @return descriptor
     * @throws Exception on malformed or unsafe XML
     */
    PomDescriptor parse(
            final Path repositoryRoot,
            final Path relativePom)
            throws Exception {
        final Path pom = repositoryRoot.resolve(
                relativePom).normalize();
        final Document document = parseDocument(pom);
        final Element project =
                document.getDocumentElement();
        final Path projectDir = pom.getParent();
        final Map<String, String> modelProperties =
                modelProperties(project, projectDir,
                        inheritedProperties(repositoryRoot, pom,
                                project, new HashSet<>()));
        final Element parent = child(
                project, "parent");
        final String group = firstNonBlank(
                interpolate(text(project, "groupId"),
                        modelProperties),
                interpolate(text(parent, "groupId"),
                        modelProperties), "?");
        final String artifact = firstNonBlank(
                interpolate(text(project, "artifactId"),
                        modelProperties), "?");
        final String version = firstNonBlank(
                interpolate(text(project, "version"),
                        modelProperties),
                interpolate(text(parent, "version"),
                        modelProperties), "?");
        final String packaging = firstNonBlank(
                interpolate(text(project, "packaging"),
                        modelProperties), "jar");
        final List<String> normal = modules(
                child(project, "modules"),
                modelProperties);
        final List<String> all =
                new ArrayList<>(normal);
        final List<String> active =
                new ArrayList<>(normal);
        final List<Element> profiles = children(
                child(project, "profiles"), "profile");
        final boolean hasNonDefault = profiles.stream()
                .anyMatch(profile -> isNonDefaultActive(
                        profile, projectDir,
                        modelProperties));
        for (Element profile : profiles) {
            final List<String> profileModules =
                    modules(child(profile, "modules"),
                            modelProperties);
            all.addAll(profileModules);
            if (isActive(profile, projectDir,
                    modelProperties, hasNonDefault)) {
                active.addAll(profileModules);
            }
        }
        return new PomDescriptor(relativePom,
                group + ":" + artifact + ":"
                        + version, packaging,
                stableDistinct(all),
                stableDistinct(active), "");
    }

    private void parseArguments(
            final List<String> arguments) {
        boolean profileValue = false;
        boolean propertyValue = false;
        for (String argument : arguments) {
            if (profileValue) {
                addProfiles(argument);
                profileValue = false;
            } else if (propertyValue) {
                addProperty(argument);
                propertyValue = false;
            } else if (argument.equals("-P")
                    || argument.equals(
                    "--activate-profiles")) {
                profileValue = true;
            } else if (argument.startsWith("-P")
                    && argument.length() > 2) {
                addProfiles(argument.substring(2));
            } else if (argument.startsWith(
                    "--activate-profiles=")) {
                addProfiles(argument.substring(
                        argument.indexOf('=') + 1));
            } else if (argument.equals("-D")
                    || argument.equals("--define")) {
                propertyValue = true;
            } else if (argument.startsWith("-D")
                    && argument.length() > 2) {
                addProperty(argument.substring(2));
            } else if (argument.startsWith(
                    "--define=")) {
                addProperty(argument.substring(
                        argument.indexOf('=') + 1));
            }
        }
    }

    private void addProfiles(final String value) {
        for (String token : value.split(",")) {
            final String id = token.trim();
            if (id.startsWith("!")
                    && id.length() > 1) {
                inactiveProfiles.add(
                        id.substring(1));
            } else if (id.startsWith("-")
                    && id.length() > 1) {
                inactiveProfiles.add(
                        id.substring(1));
            } else if (!id.isBlank()) {
                activeProfiles.add(id);
            }
        }
    }

    private void addProperty(final String value) {
        final int separator = value.indexOf('=');
        if (separator < 0) {
            properties.put(value, "true");
        } else {
            properties.put(value.substring(0,
                    separator), value.substring(
                    separator + 1));
        }
    }

    private boolean isActive(
            final Element profile,
            final Path projectDir,
            final Map<String, String> modelProperties,
            final boolean hasNonDefault) {
        final String id = text(profile, "id");
        if (inactiveProfiles.contains(id)) {
            return false;
        }
        if (isNonDefaultActive(profile,
                projectDir, modelProperties)) {
            return true;
        }
        final Element activation = child(
                profile, "activation");
        return activation != null
                && "true".equalsIgnoreCase(text(
                activation, "activeByDefault"))
                && !hasNonDefault;
    }

    private boolean isNonDefaultActive(
            final Element profile,
            final Path projectDir,
            final Map<String, String> modelProperties) {
        final String id = text(profile, "id");
        if (inactiveProfiles.contains(id)) {
            return false;
        }
        if (activeProfiles.contains(id)) {
            return true;
        }
        final Element activation = child(
                profile, "activation");
        if (activation == null) {
            return false;
        }
        boolean present = false;
        boolean matches = true;
        final String jdk = text(activation, "jdk");
        if (!jdk.isBlank()) {
            present = true;
            matches &= matchesJdk(jdk,
                    System.getProperty("java.version", ""));
        }
        final Element os = child(activation, "os");
        if (os != null) {
            present = true;
            matches &= matchesOs(os);
        }
        final Element property = child(
                activation, "property");
        if (property != null) {
            present = true;
            matches &= matchesProperty(property,
                    modelProperties);
        }
        final Element file = child(activation, "file");
        if (file != null) {
            present = true;
            matches &= matchesFile(file, projectDir,
                    modelProperties);
        }
        return present && matches;
    }

    private boolean matchesProperty(
            final Element property,
            final Map<String, String> modelProperties) {
        final String name = interpolate(
                text(property, "name"),
                modelProperties);
        final String expected = interpolate(
                text(property, "value"),
                modelProperties);
        if (name.startsWith("!")) {
            return !modelProperties.containsKey(
                    name.substring(1));
        }
        final String actual = modelProperties.get(name);
        if (expected.startsWith("!")) {
            return !expected.substring(1).equals(actual);
        }
        return actual != null
                && (expected.isBlank()
                || expected.equals(actual));
    }

    private boolean matchesFile(
            final Element file,
            final Path projectDir,
            final Map<String, String> modelProperties) {
        final String exists = interpolate(
                text(file, "exists"), modelProperties);
        final String missing = interpolate(
                text(file, "missing"), modelProperties);
        boolean result = true;
        if (!exists.isBlank()) {
            result = Files.exists(resolveFile(
                    projectDir, exists));
        }
        if (!missing.isBlank()) {
            result &= !Files.exists(resolveFile(
                    projectDir, missing));
        }
        return result;
    }

    private Path resolveFile(
            final Path projectDir,
            final String value) {
        final Path path = Path.of(value);
        return path.isAbsolute() ? path.normalize()
                : projectDir.resolve(path).normalize();
    }

    private boolean matchesJdk(
            final String expression,
            final String javaVersion) {
        final String value = expression.trim();
        if (value.startsWith("!")) {
            return !matchesJdk(value.substring(1),
                    javaVersion);
        }
        if (!value.startsWith("[")
                && !value.startsWith("(")) {
            return javaVersion.startsWith(value);
        }
        final String[] bounds = value.substring(1,
                        value.length() - 1)
                .split(",", -1);
        if (bounds.length != 2) {
            return false;
        }
        final int lower = bounds[0].isBlank() ? 1
                : compareVersions(javaVersion,
                bounds[0]);
        final int upper = bounds[1].isBlank() ? -1
                : compareVersions(javaVersion,
                bounds[1]);
        final boolean lowerMatch = bounds[0].isBlank()
                || (value.startsWith("[")
                ? lower >= 0 : lower > 0);
        final boolean upperMatch = bounds[1].isBlank()
                || (value.endsWith("]")
                ? upper <= 0 : upper < 0);
        return lowerMatch && upperMatch;
    }

    private int compareVersions(
            final String first,
            final String second) {
        final String[] left = first.split("[^0-9]+");
        final String[] right = second.split("[^0-9]+");
        final int length = Math.max(left.length,
                right.length);
        for (int index = 0; index < length; index++) {
            final int leftPart = index < left.length
                    && !left[index].isBlank()
                    ? Integer.parseInt(left[index]) : 0;
            final int rightPart = index < right.length
                    && !right[index].isBlank()
                    ? Integer.parseInt(right[index]) : 0;
            final int result = Integer.compare(
                    leftPart, rightPart);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private boolean matchesOs(final Element os) {
        final String name = System.getProperty(
                "os.name", "").toLowerCase(Locale.ROOT);
        final String arch = System.getProperty(
                "os.arch", "").toLowerCase(Locale.ROOT);
        final String version = System.getProperty(
                "os.version", "").toLowerCase(Locale.ROOT);
        return matchesOsValue(text(os, "name"), name)
                && matchesOsValue(text(os, "arch"), arch)
                && matchesOsVersion(text(os, "version"),
                version)
                && matchesOsFamily(text(os, "family"),
                name);
    }

    private boolean matchesOsValue(
            final String expected,
            final String actual) {
        if (expected.isBlank()) {
            return true;
        }
        if (expected.startsWith("!")) {
            return !expected.substring(1)
                    .equalsIgnoreCase(actual);
        }
        return expected.equalsIgnoreCase(actual);
    }

    private boolean matchesOsVersion(
            final String expected,
            final String actual) {
        if (expected.toLowerCase(Locale.ROOT)
                .startsWith("regex:")) {
            return actual.matches(expected.substring(
                    expected.indexOf(':') + 1));
        }
        return matchesOsValue(expected, actual);
    }

    private boolean matchesOsFamily(
            final String expected,
            final String name) {
        if (expected.isBlank()) {
            return true;
        }
        final boolean negate = expected.startsWith("!");
        final String family = (negate
                ? expected.substring(1) : expected)
                .toLowerCase(Locale.ROOT);
        final boolean windows = name.contains("windows");
        final boolean match = switch (family) {
            case "windows" -> windows;
            case "win9x" -> windows
                    && (name.contains("95")
                    || name.contains("98")
                    || name.contains("me")
                    || name.contains("ce"));
            case "dos" -> name.contains("dos");
            case "mac" -> name.contains("mac");
            case "unix" -> !windows
                    && !name.contains("openvms")
                    && !name.contains("os/2");
            default -> name.contains(family);
        };
        return negate ? !match : match;
    }

    private Map<String, String> modelProperties(
            final Element project,
            final Path projectDir,
            final Map<String, String> inherited) {
        final Map<String, String> result =
                new HashMap<>();
        System.getProperties().forEach((key, value) ->
                result.put(key.toString(),
                        value.toString()));
        result.putAll(inherited);
        result.putAll(pomProperties(project));
        result.put("basedir", projectDir.toString());
        result.put("project.basedir",
                projectDir.toString());
        result.putAll(properties);
        return result;
    }

    private Map<String, String> inheritedProperties(
            final Path repositoryRoot,
            final Path pom,
            final Element project,
            final Set<Path> visited) throws Exception {
        final Path normalizedPom = pom.toAbsolutePath().normalize();
        if (!visited.add(normalizedPom)) {
            throw new IllegalStateException(
                    "Local parent POM cycle: " + pom);
        }
        final Path parentPom = localParentPom(
                repositoryRoot, normalizedPom, project);
        if (parentPom == null) {
            return Map.of();
        }
        final Document parentDocument = parseDocument(parentPom);
        final Element parentProject = parentDocument.getDocumentElement();
        final Map<String, String> result = new HashMap<>(
                inheritedProperties(repositoryRoot, parentPom,
                        parentProject, visited));
        result.putAll(pomProperties(parentProject));
        return result;
    }

    private Path localParentPom(
            final Path repositoryRoot,
            final Path pom,
            final Element project) {
        final Element parent = child(project, "parent");
        if (parent == null) {
            return null;
        }
        final Element relative = child(parent, "relativePath");
        if (relative != null && relative.getTextContent().trim().isEmpty()) {
            return null;
        }
        final String relativePath = relative == null
                ? "../pom.xml" : relative.getTextContent().trim();
        Path candidate = pom.getParent().resolve(relativePath).normalize();
        if (Files.isDirectory(candidate)) {
            candidate = candidate.resolve("pom.xml");
        }
        final Path root = repositoryRoot.toAbsolutePath().normalize();
        final Path normalized = candidate.toAbsolutePath().normalize();
        return normalized.startsWith(root) && Files.isRegularFile(normalized)
                ? normalized : null;
    }

    private Map<String, String> pomProperties(final Element project) {
        final Map<String, String> result = new HashMap<>();
        final Element pomProperties = child(project, "properties");
        if (pomProperties != null) {
            final NodeList nodes = pomProperties
                    .getChildNodes();
            for (int index = 0;
                 index < nodes.getLength(); index++) {
                final Node node = nodes.item(index);
                if (node instanceof Element) {
                    result.put(nodeName(node), node
                            .getTextContent().trim());
                }
            }
        }
        return result;
    }

    private Document parseDocument(final Path pom) throws Exception {
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/"
                        + "disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/"
                        + "external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/"
                        + "external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(final SAXParseException exception) {
                // Warnings do not invalidate the minimal model.
            }

            @Override
            public void error(final SAXParseException exception)
                    throws SAXParseException {
                throw exception;
            }

            @Override
            public void fatalError(final SAXParseException exception)
                    throws SAXParseException {
                throw exception;
            }
        });
        return builder.parse(pom.toFile());
    }

    private String interpolate(
            final String value,
            final Map<String, String> modelProperties) {
        String result = value;
        for (Map.Entry<String, String> entry
                : modelProperties.entrySet()) {
            result = result.replace("${"
                    + entry.getKey() + "}",
                    entry.getValue());
        }
        return result;
    }

    private List<String> modules(
            final Element modules,
            final Map<String, String> modelProperties) {
        final List<String> result =
                new ArrayList<>();
        for (Element module
                : children(modules, "module")) {
            final String value = interpolate(module
                    .getTextContent().trim(),
                    modelProperties);
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> stableDistinct(
            final List<String> values) {
        return values.stream().distinct()
                .sorted().toList();
    }

    private String firstNonBlank(
            final String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(
            final Element element,
            final String name) {
        final Element value = child(element, name);
        return value == null ? ""
                : value.getTextContent().trim();
    }

    private Element child(
            final Element element,
            final String name) {
        if (element == null) {
            return null;
        }
        final NodeList nodes = element.getChildNodes();
        for (int index = 0;
             index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            if (node instanceof Element
                    && name.equals(nodeName(node))) {
                return (Element) node;
            }
        }
        return null;
    }

    private List<Element> children(
            final Element element,
            final String name) {
        final List<Element> result =
                new ArrayList<>();
        if (element == null) {
            return result;
        }
        final NodeList nodes = element.getChildNodes();
        for (int index = 0;
             index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            if (node instanceof Element
                    && name.equals(nodeName(node))) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private String nodeName(final Node node) {
        return node.getLocalName() == null
                ? node.getNodeName()
                : node.getLocalName();
    }
}
