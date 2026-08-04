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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Wiki: wiki/features/maven-runtime.md - tree plugin runtime preparation
/** Prepares the offline Maven Dependency Plugin repository and settings. */
public final class MavenDependencyPluginRuntimeManager {

    /** Bundled plugin version. */
    public static final String EMBEDDED_VERSION = "3.6.1";

    /** Plugin group. */
    private static final String PLUGIN_GROUP =
            "org.apache.maven.plugins";

    /** Plugin artifact. */
    private static final String PLUGIN_ARTIFACT =
            "maven-dependency-plugin";

    /** Built-in Artifact Path Plugin group. */
    private static final String ARTIFACT_PATH_GROUP =
            "io.github.dependencyanalysis";

    /** Built-in Artifact Path Plugin artifact. */
    private static final String ARTIFACT_PATH_ARTIFACT =
            "dependency-analyzer-artifact-path-maven-plugin";

    /** Built-in Artifact Path Plugin version. */
    private static final String ARTIFACT_PATH_VERSION = "1.0.0";

    /** Built-in Artifact Path Plugin resource base. */
    private static final String ARTIFACT_PATH_RESOURCE_BASE =
            "/maven/artifact-path-plugin/"
                    + ARTIFACT_PATH_ARTIFACT + "-"
                    + ARTIFACT_PATH_VERSION;

    /** Built-in Artifact Path Plugin JAR resource. */
    private static final String ARTIFACT_PATH_JAR_RESOURCE =
            ARTIFACT_PATH_RESOURCE_BASE + ".jar";

    /** Built-in Artifact Path Plugin consumer POM resource. */
    private static final String ARTIFACT_PATH_POM_RESOURCE =
            ARTIFACT_PATH_RESOURCE_BASE + ".pom";

    /** Repository archive resource. */
    private static final String ZIP_RESOURCE =
            "/maven/dependency-plugin/"
                    + "maven-dependency-plugin-3.6.1-repository.zip";

    /** Repository checksum resource. */
    private static final String SHA_RESOURCE =
            ZIP_RESOURCE + ".sha512";

    /** Extracted repository directory. */
    private static final String REPOSITORY_DIRECTORY =
            "repository";

    /** Complete extraction marker. */
    private static final String COMPLETE_MARKER =
            ".dependency-analyzer-complete";

    /** Tool settings profile prefix. */
    private static final String PROFILE_PREFIX =
            "dependency-analyzer-plugin-";

    /** Resource checksum buffer. */
    private static final int BUFFER_SIZE = 8192;

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
    private static final ConcurrentMap<Path, Object>
            JVM_LOCKS = new ConcurrentHashMap<>();

    /**
     * Prepares the embedded runtime or validates an override.
     *
     * @param configDir complete application config directory
     * @param arguments validated user Maven arguments
     * @param configuredVersion optional version override
     * @return prepared plugin execution contract
     */
    public MavenDependencyPluginRuntime prepare(
            final Path configDir,
            final List<String> arguments,
            final String configuredVersion) {
        final Path normalizedConfig = configDir
                .toAbsolutePath().normalize();
        final String override = configuredVersion == null
                ? "" : configuredVersion.trim();
        if (!override.isEmpty()
                && !supportsCompleteEvidence(override)) {
            throw new MavenRuntimeException(
                    "maven-dependency-plugin " + override
                            + " does not provide complete"
                            + " dependency evidence");
        }
        try {
            Files.createDirectories(normalizedConfig);
            final String archiveChecksum = readChecksum(SHA_RESOURCE);
            final String jarChecksum = readChecksum(
                    ARTIFACT_PATH_JAR_RESOURCE + ".sha512");
            final String pomChecksum = readChecksum(
                    ARTIFACT_PATH_POM_RESOURCE + ".sha512");
            final String checksum = fingerprint(
                    archiveChecksum, jarChecksum, pomChecksum);
            final Path repository = prepareRepository(
                    normalizedConfig, checksum,
                    archiveChecksum, jarChecksum, pomChecksum);
            final List<String> effectiveArguments =
                    overlayGlobalSettings(
                            normalizedConfig, repository,
                            checksum, arguments);
            return new MavenDependencyPluginRuntime(
                    override.isEmpty() ? EMBEDDED_VERSION : override,
                    goal(override.isEmpty()
                            ? EMBEDDED_VERSION : override),
                    effectiveArguments, repository,
                    checksum);
        } catch (IOException | ParserConfigurationException
                 | SAXException | TransformerException exception) {
            throw new MavenRuntimeException(
                    "Unable to prepare Maven Dependency Plugin runtime",
                    exception);
        }
    }

    /**
     * Tests whether a plugin version supplies complete verbose evidence.
     *
     * @param version plugin version
     * @return true when supported
     */
    public static boolean supportsCompleteEvidence(
            final String version) {
        final String normalized = version.trim();
        if (!normalized.matches("[0-9]+\\.[0-9]+"
                + "(?:\\.[0-9]+)?"
                + "(?:[-.][A-Za-z0-9]+)*")) {
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
                    || (major == PLUGIN_THREE
                    && minor >= PLUGIN_THREE_TWO);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private Path prepareRepository(
            final Path configDir,
            final String checksum,
            final String archiveChecksum,
            final String jarChecksum,
            final String pomChecksum)
            throws IOException {
        final Path versionDir = configDir
                .resolve("runtime")
                .resolve("maven-dependency-plugin")
                .resolve(EMBEDDED_VERSION);
        final Path runtimeLeaf = versionDir
                .resolve(checksum);
        final Path lockPath = configDir
                .resolve("locks")
                .resolve("maven-dependency-plugin-"
                        + EMBEDDED_VERSION + "-"
                        + checksum + ".lock");
        withLock(lockPath, () -> {
            if (!isComplete(runtimeLeaf, checksum,
                    jarChecksum, pomChecksum)) {
                deleteManagedLeaf(runtimeLeaf,
                        versionDir);
                extract(runtimeLeaf, versionDir, checksum,
                        archiveChecksum, jarChecksum, pomChecksum);
            }
        });
        return runtimeLeaf.resolve(
                REPOSITORY_DIRECTORY);
    }

    private boolean isComplete(
            final Path runtimeLeaf,
            final String checksum,
            final String jarChecksum,
            final String pomChecksum) {
        final Path marker = runtimeLeaf
                .resolve(COMPLETE_MARKER);
        final Path pluginJar = runtimeLeaf
                .resolve(REPOSITORY_DIRECTORY)
                .resolve("org/apache/maven/plugins/")
                .resolve(PLUGIN_ARTIFACT)
                .resolve(EMBEDDED_VERSION)
                .resolve(PLUGIN_ARTIFACT + "-"
                        + EMBEDDED_VERSION + ".jar");
        final Path artifactPathDirectory = artifactPathDirectory(
                runtimeLeaf.resolve(REPOSITORY_DIRECTORY));
        final Path artifactPathJar = artifactPathDirectory.resolve(
                ARTIFACT_PATH_ARTIFACT + "-"
                        + ARTIFACT_PATH_VERSION + ".jar");
        final Path artifactPathPom = artifactPathDirectory.resolve(
                ARTIFACT_PATH_ARTIFACT + "-"
                        + ARTIFACT_PATH_VERSION + ".pom");
        try {
            return Files.isRegularFile(pluginJar,
                    LinkOption.NOFOLLOW_LINKS)
                    && checksum.equals(
                    Files.readString(marker).trim())
                    && jarChecksum.equals(fileChecksum(
                    artifactPathJar, "SHA-512"))
                    && pomChecksum.equals(fileChecksum(
                    artifactPathPom, "SHA-512"))
                    && matchesEmbeddedRepository(
                    runtimeLeaf);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean matchesEmbeddedRepository(
            final Path runtimeLeaf)
            throws IOException {
        try (InputStream raw = resource(ZIP_RESOURCE);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path installed = runtimeLeaf
                        .resolve(entry.getName()).normalize();
                if (!installed.startsWith(runtimeLeaf)) {
                    return false;
                }
                if (entry.isDirectory()) {
                    if (!Files.isDirectory(installed,
                            LinkOption.NOFOLLOW_LINKS)) {
                        return false;
                    }
                } else if (!Files.isRegularFile(installed,
                        LinkOption.NOFOLLOW_LINKS)
                        || !entryMatches(zip, installed)) {
                    return false;
                }
                zip.closeEntry();
            }
            return true;
        }
    }

    private boolean entryMatches(
            final ZipInputStream zip,
            final Path installed)
            throws IOException {
        final MessageDigest expected = digest("SHA-512");
        final byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = zip.read(buffer)) >= 0) {
            if (count > 0) {
                expected.update(buffer, 0, count);
            }
        }
        final MessageDigest actual = digest("SHA-512");
        try (InputStream input = Files.newInputStream(installed)) {
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    actual.update(buffer, 0, count);
                }
            }
        }
        return MessageDigest.isEqual(expected.digest(),
                actual.digest());
    }

    private void extract(
            final Path runtimeLeaf,
            final Path versionDir,
            final String checksum,
            final String archiveChecksum,
            final String jarChecksum,
            final String pomChecksum)
            throws IOException {
        Files.createDirectories(versionDir);
        final Path staging = versionDir.resolve(
                ".staging-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            if (!archiveChecksum.equals(resourceChecksum(
                    ZIP_RESOURCE, "SHA-512"))) {
                throw new IOException(
                        "Embedded plugin repository"
                                + " SHA-512 mismatch");
            }
            extractZip(staging);
            installArtifactPathPlugin(staging, jarChecksum,
                    pomChecksum);
            Files.writeString(staging.resolve(
                    COMPLETE_MARKER), checksum);
            move(staging, runtimeLeaf);
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
        }
    }

    private void extractZip(final Path destination)
            throws IOException {
        try (InputStream raw = resource(ZIP_RESOURCE);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path output = destination
                        .resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException(
                            "Unsafe plugin repository ZIP entry: "
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

    private void installArtifactPathPlugin(
            final Path destination,
            final String jarChecksum,
            final String pomChecksum) throws IOException {
        if (!jarChecksum.equals(resourceChecksum(
                ARTIFACT_PATH_JAR_RESOURCE, "SHA-512"))
                || !pomChecksum.equals(resourceChecksum(
                ARTIFACT_PATH_POM_RESOURCE, "SHA-512"))) {
            throw new IOException(
                    "Embedded Artifact Path Plugin SHA-512 mismatch");
        }
        final Path directory = artifactPathDirectory(
                destination.resolve(REPOSITORY_DIRECTORY));
        Files.createDirectories(directory);
        installResource(directory,
                ARTIFACT_PATH_ARTIFACT + "-"
                        + ARTIFACT_PATH_VERSION + ".jar",
                ARTIFACT_PATH_JAR_RESOURCE, jarChecksum);
        installResource(directory,
                ARTIFACT_PATH_ARTIFACT + "-"
                        + ARTIFACT_PATH_VERSION + ".pom",
                ARTIFACT_PATH_POM_RESOURCE, pomChecksum);
    }

    private void installResource(
            final Path directory,
            final String filename,
            final String resourceName,
            final String sha512) throws IOException {
        final Path output = directory.resolve(filename);
        try (InputStream input = resource(resourceName)) {
            Files.copy(input, output,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(output.resolveSibling(filename + ".sha512"),
                sha512 + System.lineSeparator(),
                StandardCharsets.US_ASCII);
        Files.writeString(output.resolveSibling(filename + ".sha1"),
                fileChecksum(output, "SHA-1")
                        + System.lineSeparator(),
                StandardCharsets.US_ASCII);
    }

    private Path artifactPathDirectory(final Path repository) {
        return repository.resolve(ARTIFACT_PATH_GROUP.replace('.', '/'))
                .resolve(ARTIFACT_PATH_ARTIFACT)
                .resolve(ARTIFACT_PATH_VERSION);
    }

    private List<String> overlayGlobalSettings(
            final Path configDir,
            final Path repository,
            final String repositoryChecksum,
            final List<String> arguments)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerException {
        final GlobalSettingsArguments parsed =
                GlobalSettingsArguments.parse(arguments);
        final Document document = settingsDocument(
                parsed.getSettings());
        addPluginRepository(document, repository,
                repositoryChecksum);
        final byte[] content = serialize(document);
        final String contentChecksum = toHex(
                digest("SHA-512").digest(content));
        final Path settingsDir = configDir
                .resolve("runtime")
                .resolve("maven-dependency-plugin")
                .resolve("settings");
        final Path settings = settingsDir.resolve(
                contentChecksum + ".xml");
        final Path lockPath = configDir
                .resolve("locks")
                .resolve("maven-dependency-plugin-settings-"
                        + contentChecksum + ".lock");
        withLock(lockPath, () -> writeSettings(
                settings, content));
        final List<String> result = new ArrayList<>(
                parsed.getRemainingArguments());
        result.add("-gs");
        result.add(settings.toString());
        return List.copyOf(result);
    }

    private Document settingsDocument(
            final Path userSettings)
            throws ParserConfigurationException,
            IOException, SAXException {
        final DocumentBuilderFactory factory =
                secureDocumentBuilderFactory();
        if (userSettings != null) {
            try (InputStream input = Files.newInputStream(
                    userSettings)) {
                return factory.newDocumentBuilder()
                        .parse(input);
            }
        }
        final String emptySettings = "<?xml version=\"1.0\""
                + " encoding=\"UTF-8\"?>"
                + "<settings xmlns=\"http://maven.apache.org/"
                + "SETTINGS/1.0.0\"/>";
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(emptySettings
                        .getBytes(StandardCharsets.UTF_8)));
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/"
                        + "disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/"
                        + "external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/"
                        + "external-parameter-entities", false);
        factory.setFeature(
                "http://apache.org/xml/features/"
                        + "nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private void addPluginRepository(
            final Document document,
            final Path repository,
            final String checksum) {
        final Element root = document.getDocumentElement();
        final String namespace = root.getNamespaceURI();
        Element profiles = directChild(root, "profiles");
        if (profiles == null) {
            profiles = element(document, namespace,
                    "profiles");
            final Element activeProfiles = directChild(
                    root, "activeProfiles");
            root.insertBefore(profiles, activeProfiles);
        }
        final String profileId = PROFILE_PREFIX
                + checksum.substring(0, 12);
        excludeRepositoryFromMirrors(root,
                profileId);
        final Element profile = element(document,
                namespace, "profile");
        appendText(document, profile, namespace,
                "id", profileId);
        final Element repositories = element(document,
                namespace, "pluginRepositories");
        final Element pluginRepository = element(document,
                namespace, "pluginRepository");
        appendText(document, pluginRepository, namespace,
                "id", profileId);
        appendText(document, pluginRepository, namespace,
                "url", repository.toUri()
                        .toASCIIString());
        final Element releases = element(document,
                namespace, "releases");
        appendText(document, releases, namespace,
                "enabled", "true");
        appendText(document, releases, namespace,
                "updatePolicy", "always");
        appendText(document, releases, namespace,
                "checksumPolicy", "fail");
        pluginRepository.appendChild(releases);
        final Element snapshots = element(document,
                namespace, "snapshots");
        appendText(document, snapshots, namespace,
                "enabled", "false");
        pluginRepository.appendChild(snapshots);
        repositories.appendChild(pluginRepository);
        profile.appendChild(repositories);
        profiles.appendChild(profile);

        Element activeProfiles = directChild(
                root, "activeProfiles");
        if (activeProfiles == null) {
            activeProfiles = element(document,
                    namespace, "activeProfiles");
            root.appendChild(activeProfiles);
        }
        appendText(document, activeProfiles, namespace,
                "activeProfile", profileId);
    }

    private void excludeRepositoryFromMirrors(
            final Element root,
            final String repositoryId) {
        final Element mirrors = directChild(
                root, "mirrors");
        if (mirrors == null) {
            return;
        }
        final NodeList children = mirrors.getChildNodes();
        for (int index = 0;
             index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (!(child instanceof Element)
                    || !"mirror".equals(child.getLocalName() == null
                    ? child.getNodeName()
                    : child.getLocalName())) {
                continue;
            }
            final Element mirrorOf = directChild(
                    (Element) child, "mirrorOf");
            if (mirrorOf != null
                    && !mirrorOf.getTextContent().contains(
                    "!" + repositoryId)) {
                mirrorOf.setTextContent(
                        mirrorOf.getTextContent().trim()
                                + ",!" + repositoryId);
            }
        }
    }

    private Element directChild(
            final Element parent,
            final String name) {
        final NodeList children = parent.getChildNodes();
        for (int index = 0;
             index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child instanceof Element
                    && name.equals(child.getLocalName() == null
                    ? child.getNodeName()
                    : child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private Element element(
            final Document document,
            final String namespace,
            final String name) {
        return namespace == null
                ? document.createElement(name)
                : document.createElementNS(namespace, name);
    }

    private void appendText(
            final Document document,
            final Element parent,
            final String namespace,
            final String name,
            final String value) {
        final Element child = element(document,
                namespace, name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private byte[] serialize(final Document document)
            throws TransformerException {
        final TransformerFactory factory =
                TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                true);
        final Transformer transformer =
                factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT,
                "yes");
        final ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document),
                new StreamResult(output));
        return output.toByteArray();
    }

    private void writeSettings(
            final Path settings,
            final byte[] content)
            throws IOException {
        if (Files.isRegularFile(settings)
                && Arrays.equals(Files.readAllBytes(settings),
                content)) {
            return;
        }
        Files.createDirectories(settings.getParent());
        final Path staging = settings.getParent().resolve(
                ".staging-" + UUID.randomUUID() + ".xml");
        try {
            Files.write(staging, content,
                    StandardOpenOption.CREATE_NEW);
            restrictSettingsPermissions(staging);
            replace(staging, settings);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private void restrictSettingsPermissions(
            final Path settings) {
        try {
            Files.setPosixFilePermissions(settings,
                    EnumSet.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException
                 | IOException ignored) {
            // Non-POSIX file systems use platform defaults.
        }
    }

    private String readChecksum(final String checksumResource)
            throws IOException {
        try (InputStream stream = resource(checksumResource)) {
            final String value = new String(
                    stream.readAllBytes(),
                    StandardCharsets.US_ASCII).trim();
            final String first = value.split("\\s+")[0]
                    .toLowerCase(Locale.ROOT);
            if (!first.matches("[0-9a-f]{128}")) {
                throw new IOException(
                        "Invalid embedded plugin SHA-512");
            }
            return first;
        }
    }

    private String resourceChecksum(
            final String resourceName,
            final String algorithm)
            throws IOException {
        final MessageDigest value = digest(algorithm);
        try (InputStream input = resource(resourceName)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    value.update(buffer, 0, count);
                }
            }
        }
        return toHex(value.digest());
    }

    private String fileChecksum(
            final Path file,
            final String algorithm) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        final MessageDigest value = digest(algorithm);
        try (InputStream input = Files.newInputStream(file)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    value.update(buffer, 0, count);
                }
            }
        }
        return toHex(value.digest());
    }

    private String fingerprint(final String... checksums) {
        final MessageDigest value = digest("SHA-512");
        for (String checksum : checksums) {
            value.update(checksum.getBytes(StandardCharsets.US_ASCII));
            value.update((byte) '\n');
        }
        return toHex(value.digest());
    }

    private InputStream resource(final String name)
            throws IOException {
        final InputStream stream =
                MavenDependencyPluginRuntimeManager.class
                        .getResourceAsStream(name);
        if (stream == null) {
            throw new IOException(
                    "Missing resource: " + name);
        }
        return stream;
    }

    private MessageDigest digest(
            final String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String toHex(final byte[] bytes) {
        final StringBuilder value = new StringBuilder();
        for (byte item : bytes) {
            value.append(String.format(Locale.ROOT,
                    "%02x", item));
        }
        return value.toString();
    }

    private String goal(final String version) {
        return PLUGIN_GROUP + ":" + PLUGIN_ARTIFACT
                + ":" + version + ":tree";
    }

    private void withLock(
            final Path lockPath,
            final IoAction action)
            throws IOException {
        Files.createDirectories(lockPath.getParent());
        final Path normalized = lockPath
                .toAbsolutePath().normalize();
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

    private void move(
            final Path source,
            final Path target)
            throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException
                 | DirectoryNotEmptyException exception) {
            Files.move(source, target);
        }
    }

    private void replace(
            final Path source,
            final Path target)
            throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteManagedLeaf(
            final Path runtimeLeaf,
            final Path versionDir)
            throws IOException {
        final Path normalizedLeaf = runtimeLeaf
                .toAbsolutePath().normalize();
        final Path normalizedVersion = versionDir
                .toAbsolutePath().normalize();
        if (!normalizedLeaf.getParent().equals(
                normalizedVersion)) {
            throw new IOException(
                    "Refusing unmanaged plugin runtime cleanup");
        }
        if (Files.exists(normalizedLeaf)) {
            deleteTree(normalizedLeaf);
        }
    }

    private void deleteTree(final Path root)
            throws IOException {
        try (java.util.stream.Stream<Path> stream =
                     Files.walk(root)) {
            final Path[] paths = stream.sorted(
                    Comparator.reverseOrder())
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

        /** @return user global settings */
        Path getSettings() {
            return settings;
        }

        /** @return arguments excluding global settings */
        List<String> getRemainingArguments() {
            return remainingArguments;
        }

        static GlobalSettingsArguments parse(
                final List<String> arguments) {
            final List<String> remaining =
                    new ArrayList<>();
            Path settings = null;
            for (int index = 0;
                 index < arguments.size(); index++) {
                final String argument = arguments.get(index);
                final String lower = argument
                        .toLowerCase(Locale.ROOT);
                if (lower.equals("-gs")
                        || lower.equals("--global-settings")) {
                    settings = Path.of(arguments.get(++index));
                } else if (lower.startsWith(
                        "--global-settings=")) {
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
            return new GlobalSettingsArguments(
                    settings, remaining);
        }
    }
}
