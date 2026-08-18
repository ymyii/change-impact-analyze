package io.github.dependencyanalysis.runtime;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Wiki: wiki/features/maven-runtime.md - 内嵌 Plugin repository 准备入口
/** Prepares two offline Maven Plugin repositories and a settings overlay. */
public final class MavenDependencyPluginRuntimeManager {

    /** Bundled Maven Dependency Plugin version. */
    public static final String EMBEDDED_VERSION = "3.6.1";

    /** Maven Dependency Plugin coordinates. */
    private static final String DEPENDENCY_COMPONENT =
            "maven-dependency-plugin";

    /** Maven Dependency Plugin repository archive. */
    private static final String DEPENDENCY_REPOSITORY_RESOURCE =
            "/maven/plugin-repositories/"
                    + "maven-dependency-plugin-3.6.1-repository.zip";

    /** Dependency Evidence Plugin coordinates. */
    private static final String ARTIFACT_PATH_COMPONENT =
            "dependency-analyzer-artifact-path-maven-plugin";

    /** Dependency Evidence Plugin version. */
    private static final String ARTIFACT_PATH_VERSION =
            MavenDependencyPluginRuntime.ARTIFACT_PATH_PLUGIN_VERSION;

    /** Dependency Evidence Plugin repository archive. */
    private static final String ARTIFACT_PATH_REPOSITORY_RESOURCE =
            "/maven/plugin-repositories/"
                    + ARTIFACT_PATH_COMPONENT + "-"
                    + ARTIFACT_PATH_VERSION + "-repository.zip";

    /** Extracted repository directory. */
    private static final String REPOSITORY_DIRECTORY = "repository";

    /** Complete extraction marker. */
    private static final String COMPLETE_MARKER =
            ".dependency-analyzer-complete";

    /** Tool settings profile. */
    private static final String PROFILE_ID =
            "dependency-analyzer-embedded-plugins";

    /** Minimum complete evidence plugin two minor. */
    private static final int PLUGIN_TWO_NINE = 9;

    /** Maven Dependency Plugin two major. */
    private static final int PLUGIN_TWO = 2;

    /** Second complete evidence plugin two minor. */
    private static final int PLUGIN_TWO_TEN = 10;

    /** Complete evidence plugin three minor. */
    private static final int PLUGIN_THREE_TWO = 2;

    /** Maven Dependency Plugin three major. */
    private static final int PLUGIN_THREE = 3;

    /** Length of the two-letter global settings option. */
    private static final int GLOBAL_SETTINGS_OPTION_LENGTH = 3;

    /** JVM guards supplementing cross-process file locks. */
    private static final ConcurrentMap<Path, Object> JVM_LOCKS =
            new ConcurrentHashMap<>();

    /**
     * Prepares the built-in repositories and validates an override.
     *
     * @param configDir complete application config directory
     * @param arguments validated user Maven arguments
     * @param configuredVersion optional dependency plugin version override
     * @return prepared plugin execution contract
     */
    public MavenDependencyPluginRuntime prepare(
            final Path configDir,
            final List<String> arguments,
            final String configuredVersion) {
        return prepare(configDir, arguments, configuredVersion, null);
    }

    /**
     * Prepares plugin repositories while preserving runtime global settings.
     *
     * @param configDir complete application config directory
     * @param arguments validated user Maven arguments
     * @param configuredVersion optional dependency plugin version override
     * @param defaultGlobalSettings runtime default global settings, nullable
     * @return prepared plugin execution contract
     */
    public MavenDependencyPluginRuntime prepare(
            final Path configDir,
            final List<String> arguments,
            final String configuredVersion,
            final Path defaultGlobalSettings) {
        final Path normalizedConfig = configDir
                .toAbsolutePath().normalize();
        final String override = configuredVersion == null
                ? "" : configuredVersion.trim();
        if (!override.isEmpty() && !supportsCompleteEvidence(override)) {
            throw new MavenRuntimeException(
                    "maven-dependency-plugin " + override
                            + " does not provide complete dependency evidence");
        }
        final RepositoryArchive dependencyArchive = dependencyArchive();
        final RepositoryArchive artifactPathArchive = artifactPathArchive();
        try {
            Files.createDirectories(normalizedConfig);
            final PreparedRepository dependency = prepareRepository(
                    normalizedConfig, dependencyArchive);
            final PreparedRepository artifactPath = prepareRepository(
                    normalizedConfig, artifactPathArchive);
            final List<PreparedRepository> repositories = List.of(
                    dependency, artifactPath);
            final SettingsOverlay overlay = overlayGlobalSettings(
                    normalizedConfig, repositories, arguments,
                    defaultGlobalSettings);
            final List<Path> cleanup = new ArrayList<>();
            cleanup.add(overlay.settings);
            if (artifactPathArchive.snapshot) {
                cleanup.add(artifactPath.runtimeLeaf);
            }
            final String selected = override.isEmpty()
                    ? EMBEDDED_VERSION : override;
            return new MavenDependencyPluginRuntime(
                    selected, goal(selected), overlay.arguments,
                    repositories.stream()
                            .map(repository -> repository.repository)
                            .toList(), cleanup);
        } catch (IOException | ParserConfigurationException
                 | SAXException | TransformerException exception) {
            throw new MavenRuntimeException(
                    "Unable to prepare Maven Plugin repositories", exception);
        }
    }

    /**
     * Tests whether a plugin version supplies complete verbose evidence.
     *
     * @param version plugin version
     * @return true when supported
     */
    public static boolean supportsCompleteEvidence(final String version) {
        final String normalized = version.trim();
        if (!normalized.matches("[0-9]+\\.[0-9]+"
                + "(?:\\.[0-9]+)?(?:[-.][A-Za-z0-9]+)*")) {
            return false;
        }
        final String[] parts = normalized.split("[.-]");
        if (parts.length < 2) {
            return false;
        }
        try {
            final int major = Integer.parseInt(parts[0]);
            final int minor = Integer.parseInt(parts[1]);
            if (major == PLUGIN_TWO) {
                return minor == PLUGIN_TWO_NINE
                        || minor == PLUGIN_TWO_TEN;
            }
            return major > PLUGIN_THREE
                    || major == PLUGIN_THREE && minor >= PLUGIN_THREE_TWO;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private RepositoryArchive dependencyArchive() {
        return new RepositoryArchive(
                DEPENDENCY_COMPONENT, EMBEDDED_VERSION,
                DEPENDENCY_REPOSITORY_RESOURCE, false,
                List.of("repository/org/apache/maven/plugins/"
                        + DEPENDENCY_COMPONENT + "/" + EMBEDDED_VERSION + "/"
                        + DEPENDENCY_COMPONENT + "-" + EMBEDDED_VERSION
                        + ".jar",
                        "repository/org/apache/maven/plugins/"
                                + DEPENDENCY_COMPONENT + "/"
                                + EMBEDDED_VERSION + "/"
                                + DEPENDENCY_COMPONENT + "-"
                                + EMBEDDED_VERSION + ".pom"));
    }

    private RepositoryArchive artifactPathArchive() {
        final String base = "repository/io/github/dependencyanalysis/"
                + ARTIFACT_PATH_COMPONENT + "/" + ARTIFACT_PATH_VERSION + "/"
                + ARTIFACT_PATH_COMPONENT + "-" + ARTIFACT_PATH_VERSION;
        return new RepositoryArchive(
                ARTIFACT_PATH_COMPONENT, ARTIFACT_PATH_VERSION,
                ARTIFACT_PATH_REPOSITORY_RESOURCE,
                ARTIFACT_PATH_VERSION.endsWith("-SNAPSHOT"),
                List.of(base + ".jar", base + ".pom"));
    }

    private PreparedRepository prepareRepository(
            final Path configDir,
            final RepositoryArchive archive) throws IOException {
        final Path versionDir = configDir.resolve("runtime")
                .resolve("plugin-repositories")
                .resolve(archive.component)
                .resolve(archive.version);
        final Path runtimeLeaf = archive.snapshot
                ? versionDir.resolve("runs")
                .resolve(UUID.randomUUID().toString())
                : versionDir.resolve("content");
        final Path lockPath = configDir.resolve("locks").resolve(
                archive.component + "-" + archive.version + ".lock");
        withLock(lockPath, () -> {
            if (archive.snapshot || !isComplete(runtimeLeaf, archive)) {
                deleteManagedLeaf(runtimeLeaf, versionDir);
                extract(runtimeLeaf, versionDir, archive);
            }
        });
        return new PreparedRepository(
                archive, runtimeLeaf,
                runtimeLeaf.resolve(REPOSITORY_DIRECTORY));
    }

    private boolean isComplete(
            final Path runtimeLeaf,
            final RepositoryArchive archive) {
        final Path marker = runtimeLeaf.resolve(COMPLETE_MARKER);
        try {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || !archive.marker().equals(
                    Files.readString(marker).trim())) {
                return false;
            }
            for (String required : archive.requiredFiles) {
                if (!Files.isRegularFile(runtimeLeaf.resolve(required),
                        LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void extract(
            final Path runtimeLeaf,
            final Path versionDir,
            final RepositoryArchive archive) throws IOException {
        Files.createDirectories(versionDir);
        final Path staging = versionDir.resolve(
                ".staging-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            extractZip(staging, archive.resource);
            for (String required : archive.requiredFiles) {
                if (!Files.isRegularFile(staging.resolve(required),
                        LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Repository archive is missing: "
                            + required);
                }
            }
            Files.writeString(staging.resolve(COMPLETE_MARKER),
                    archive.marker());
            Files.createDirectories(runtimeLeaf.getParent());
            move(staging, runtimeLeaf);
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
        }
    }

    private void extractZip(
            final Path destination,
            final String resourceName) throws IOException {
        try (InputStream raw = resource(resourceName);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path output = destination.resolve(entry.getName())
                        .normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException("Unsafe repository ZIP entry: "
                            + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output,
                            StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private SettingsOverlay overlayGlobalSettings(
            final Path configDir,
            final List<PreparedRepository> repositories,
            final List<String> arguments,
            final Path defaultGlobalSettings)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerException {
        final GlobalSettingsArguments parsed =
                GlobalSettingsArguments.parse(arguments);
        final Path sourceSettings = parsed.settings == null
                ? defaultGlobalSettings : parsed.settings;
        final Document document = settingsDocument(sourceSettings);
        addPluginRepositories(document, repositories);
        final byte[] content = serialize(document);
        final Path settingsDir = configDir.resolve("runtime")
                .resolve("plugin-repositories").resolve("settings");
        Files.createDirectories(settingsDir);
        final Path settings = Files.createTempFile(
                settingsDir, "command-", ".xml");
        Files.write(settings, content, StandardOpenOption.TRUNCATE_EXISTING);
        restrictSettingsPermissions(settings);
        final List<String> result = new ArrayList<>(
                parsed.remainingArguments);
        if (repositories.stream().anyMatch(
                repository -> repository.archive.snapshot)
                && result.stream().noneMatch(argument -> "-U".equals(argument)
                || "--update-snapshots".equals(argument))) {
            result.add("-U");
        }
        result.add("-gs");
        result.add(settings.toString());
        return new SettingsOverlay(List.copyOf(result), settings);
    }

    private Document settingsDocument(final Path userSettings)
            throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilderFactory factory =
                secureDocumentBuilderFactory();
        if (userSettings != null) {
            try (InputStream input = Files.newInputStream(userSettings)) {
                return factory.newDocumentBuilder().parse(input);
            }
        }
        final String emptySettings = "<?xml version=\"1.0\""
                + " encoding=\"UTF-8\"?>"
                + "<settings xmlns=\"http://maven.apache.org/"
                + "SETTINGS/1.0.0\"/>";
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(
                        emptySettings.getBytes(StandardCharsets.UTF_8)));
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/"
                + "disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/"
                + "external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/"
                + "external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/"
                + "nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private void addPluginRepositories(
            final Document document,
            final List<PreparedRepository> repositories) {
        final Element root = document.getDocumentElement();
        final String namespace = root.getNamespaceURI();
        Element profiles = directChild(root, "profiles");
        if (profiles == null) {
            profiles = element(document, namespace, "profiles");
            root.insertBefore(profiles, directChild(root, "activeProfiles"));
        }
        final Element profile = element(document, namespace, "profile");
        appendText(document, profile, namespace, "id", PROFILE_ID);
        final Element pluginRepositories = element(
                document, namespace, "pluginRepositories");
        for (PreparedRepository repository : repositories) {
            final String repositoryId = repository.archive.repositoryId();
            excludeRepositoryFromMirrors(root, repositoryId);
            pluginRepositories.appendChild(pluginRepository(
                    document, namespace, repositoryId, repository));
        }
        profile.appendChild(pluginRepositories);
        profiles.appendChild(profile);
        Element activeProfiles = directChild(root, "activeProfiles");
        if (activeProfiles == null) {
            activeProfiles = element(document, namespace, "activeProfiles");
            root.appendChild(activeProfiles);
        }
        appendText(document, activeProfiles, namespace,
                "activeProfile", PROFILE_ID);
    }

    private Element pluginRepository(
            final Document document,
            final String namespace,
            final String repositoryId,
            final PreparedRepository repository) {
        final Element pluginRepository = element(
                document, namespace, "pluginRepository");
        appendText(document, pluginRepository, namespace,
                "id", repositoryId);
        appendText(document, pluginRepository, namespace,
                "url", repository.repository.toUri().toASCIIString());
        final Element releases = element(document, namespace, "releases");
        appendText(document, releases, namespace, "enabled",
                Boolean.toString(!repository.archive.snapshot));
        pluginRepository.appendChild(releases);
        final Element snapshots = element(document, namespace, "snapshots");
        appendText(document, snapshots, namespace, "enabled",
                Boolean.toString(repository.archive.snapshot));
        if (repository.archive.snapshot) {
            appendText(document, snapshots, namespace,
                    "updatePolicy", "always");
        }
        pluginRepository.appendChild(snapshots);
        return pluginRepository;
    }

    private void excludeRepositoryFromMirrors(
            final Element root,
            final String repositoryId) {
        final Element mirrors = directChild(root, "mirrors");
        if (mirrors == null) {
            return;
        }
        final NodeList children = mirrors.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (!(child instanceof Element)
                    || !"mirror".equals(localName(child))) {
                continue;
            }
            final Element mirrorOf = directChild((Element) child, "mirrorOf");
            if (mirrorOf != null && !mirrorOf.getTextContent().contains(
                    "!" + repositoryId)) {
                mirrorOf.setTextContent(mirrorOf.getTextContent().trim()
                        + ",!" + repositoryId);
            }
        }
    }

    private Element directChild(final Element parent, final String name) {
        final NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child instanceof Element && name.equals(localName(child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private String localName(final Node node) {
        return node.getLocalName() == null
                ? node.getNodeName() : node.getLocalName();
    }

    private Element element(
            final Document document,
            final String namespace,
            final String name) {
        return namespace == null ? document.createElement(name)
                : document.createElementNS(namespace, name);
    }

    private void appendText(
            final Document document,
            final Element parent,
            final String namespace,
            final String name,
            final String value) {
        final Element child = element(document, namespace, name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private byte[] serialize(final Document document)
            throws TransformerException {
        final TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        final Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document),
                new StreamResult(output));
        return output.toByteArray();
    }

    private void restrictSettingsPermissions(final Path settings) {
        try {
            Files.setPosixFilePermissions(settings,
                    EnumSet.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX file systems use platform defaults.
        }
    }

    private InputStream resource(final String name) throws IOException {
        final InputStream stream = MavenDependencyPluginRuntimeManager.class
                .getResourceAsStream(name);
        if (stream == null) {
            throw new IOException("Missing resource: " + name);
        }
        return stream;
    }

    private String goal(final String version) {
        return "org.apache.maven.plugins:maven-dependency-plugin:"
                + version + ":tree";
    }

    private void withLock(final Path lockPath, final IoAction action)
            throws IOException {
        Files.createDirectories(lockPath.getParent());
        final Path normalized = lockPath.toAbsolutePath().normalize();
        final Object jvmLock = JVM_LOCKS.computeIfAbsent(
                normalized, ignored -> new Object());
        synchronized (jvmLock) {
            try (FileChannel channel = FileChannel.open(
                    normalized, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                action.run();
            }
        }
    }

    private void move(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException
                 | DirectoryNotEmptyException exception) {
            Files.move(source, target);
        }
    }

    private void deleteManagedLeaf(
            final Path runtimeLeaf,
            final Path versionDir) throws IOException {
        final Path normalizedLeaf = runtimeLeaf.toAbsolutePath().normalize();
        final Path normalizedVersion = versionDir.toAbsolutePath().normalize();
        if (!normalizedLeaf.startsWith(normalizedVersion)
                || normalizedLeaf.equals(normalizedVersion)) {
            throw new IOException("Refusing unmanaged repository cleanup");
        }
        if (Files.exists(normalizedLeaf)) {
            deleteTree(normalizedLeaf);
        }
    }

    private void deleteTree(final Path root) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            final Path[] paths = stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** I/O action executed under a process lock. */
    @FunctionalInterface
    private interface IoAction {
        /** Executes the action. */
        void run() throws IOException;
    }

    /** Immutable embedded repository definition. */
    private static final class RepositoryArchive {
        /** Component cache key. */
        private final String component;
        /** Maven artifact version. */
        private final String version;
        /** Classpath ZIP resource. */
        private final String resource;
        /** Whether each prepare gets fresh command-scoped content. */
        private final boolean snapshot;
        /** Required repository files. */
        private final List<String> requiredFiles;

        private RepositoryArchive(
                final String archiveComponent,
                final String archiveVersion,
                final String archiveResource,
                final boolean archiveSnapshot,
                final List<String> archiveRequiredFiles) {
            component = archiveComponent;
            version = archiveVersion;
            resource = archiveResource;
            snapshot = archiveSnapshot;
            requiredFiles = List.copyOf(archiveRequiredFiles);
        }

        private String marker() {
            return component + ":" + version;
        }

        private String repositoryId() {
            return ("dependency-analyzer-" + component + "-" + version)
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]", "-");
        }
    }

    /** Prepared repository paths. */
    private static final class PreparedRepository {
        /** Archive definition. */
        private final RepositoryArchive archive;
        /** Extraction leaf. */
        private final Path runtimeLeaf;
        /** Maven file repository root. */
        private final Path repository;

        private PreparedRepository(
                final RepositoryArchive repositoryArchive,
                final Path leaf,
                final Path repositoryRoot) {
            archive = repositoryArchive;
            runtimeLeaf = leaf;
            repository = repositoryRoot;
        }
    }

    /** Command-scoped settings output. */
    private static final class SettingsOverlay {
        /** Effective Maven arguments. */
        private final List<String> arguments;
        /** Temporary settings file. */
        private final Path settings;

        private SettingsOverlay(
                final List<String> overlayArguments,
                final Path overlaySettings) {
            arguments = overlayArguments;
            settings = overlaySettings;
        }
    }

    /** Parsed global settings option and remaining arguments. */
    private static final class GlobalSettingsArguments {
        /** User global settings, nullable. */
        private final Path settings;
        /** Arguments excluding global settings. */
        private final List<String> remainingArguments;

        private GlobalSettingsArguments(
                final Path userSettings,
                final List<String> remaining) {
            settings = userSettings;
            remainingArguments = List.copyOf(remaining);
        }

        private static GlobalSettingsArguments parse(
                final List<String> arguments) {
            final List<String> remaining = new ArrayList<>();
            Path settings = null;
            for (int index = 0; index < arguments.size(); index++) {
                final String argument = arguments.get(index);
                final String lower = argument.toLowerCase(Locale.ROOT);
                if (lower.equals("-gs")
                        || lower.equals("--global-settings")) {
                    if (index + 1 >= arguments.size()) {
                        throw new IllegalArgumentException(
                                "Missing Maven global settings path");
                    }
                    settings = Path.of(arguments.get(++index));
                } else if (lower.startsWith("--global-settings=")) {
                    settings = Path.of(argument.substring(
                            argument.indexOf('=') + 1));
                } else if (lower.startsWith("-gs")
                        && argument.length()
                        > GLOBAL_SETTINGS_OPTION_LENGTH) {
                    String value = argument.substring(
                            GLOBAL_SETTINGS_OPTION_LENGTH);
                    if (value.startsWith("=")) {
                        value = value.substring(1);
                    }
                    settings = Path.of(value);
                } else {
                    remaining.add(argument);
                }
            }
            return new GlobalSettingsArguments(settings, remaining);
        }
    }
}
