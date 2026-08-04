package scripts.internal;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

// Wiki: wiki/runbooks/version-and-distribution.md - Distribution inspection and publication backend
/** Source-file Java 17 distribution verifier and atomic publisher. */
public final class DistributionTool {

    private static final String CONTRACT =
            "build-support/version-contract.properties";
    private static final String PLUGIN_PREFIX =
            "maven/artifact-path-plugin/"
                    + "dependency-analyzer-artifact-path-maven-plugin-";
    private static final int JAVA_EIGHT_MAJOR = 52;
    private static final int CLASS_MAGIC = 0xCAFEBABE;

    private DistributionTool() {
    }

    /** Executes inspect, compare, or publish. */
    public static void main(final String[] arguments) {
        try {
            if (arguments.length == 0) {
                throw new IllegalArgumentException(
                        "Expected inspect, compare, or publish");
            }
            switch (arguments[0]) {
                case "inspect" -> inspectCommand(arguments);
                case "compare" -> compareCommand(arguments);
                case "publish" -> publishCommand(arguments);
                default -> throw new IllegalArgumentException(
                        "Unknown command: " + arguments[0]);
            }
        } catch (IllegalArgumentException | IllegalStateException
                 | IOException exception) {
            System.err.println("distribution: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void inspectCommand(final String[] arguments)
            throws IOException {
        final Options options = Options.parse(arguments, 1);
        final Inspection inspection = inspect(options.root(), options.jar());
        System.out.println("Artifact verified: Analyzer "
                + inspection.analyzerVersion
                + "; Artifact Path Plugin " + inspection.pluginVersion);
        System.out.println("artifactSha512=" + inspection.artifactSha512);
        System.out.println("embeddedPluginSha512="
                + inspection.embeddedPluginSha512);
    }

    private static void compareCommand(final String[] arguments)
            throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "compare requires two JAR paths");
        }
        final String first = sha512(Paths.get(arguments[1]));
        final String second = sha512(Paths.get(arguments[2]));
        if (!first.equals(second)) {
            throw new IllegalStateException(
                    "Reproducibility check failed: first=" + first
                            + "; second=" + second);
        }
        System.out.println("Reproducibility verified: " + first);
    }

    private static void publishCommand(final String[] arguments)
            throws IOException {
        final Options options = Options.parse(arguments, 1);
        final Inspection inspection = inspect(options.root(), options.jar());
        final Path output = options.requiredPath("--output")
                .toAbsolutePath().normalize();
        final boolean eligible = options.requiredBoolean(
                "--release-eligible");
        final boolean dirty = options.requiredBoolean("--git-dirty");
        if (eligible && dirty) {
            throw new IllegalStateException(
                    "Dirty build cannot be release eligible");
        }
        if (Files.exists(output)) {
            throw new IllegalStateException(
                    "Distribution output already exists: " + output);
        }
        final Path parent = output.getParent();
        Files.createDirectories(parent);
        final Path staging = Files.createTempDirectory(parent,
                ".distribution-staging-");
        boolean published = false;
        try {
            final String base = "dependency-analyzer-"
                    + inspection.analyzerVersion;
            final Path jar = staging.resolve(base + ".jar");
            Files.copy(options.jar(), jar);
            Files.writeString(staging.resolve(base + ".jar.sha512"),
                    inspection.artifactSha512 + System.lineSeparator(),
                    StandardCharsets.US_ASCII);
            final String manifest = manifest(inspection, eligible, dirty,
                    options.required("--git-commit"),
                    options.required("--java-version"),
                    options.required("--maven-version"));
            Files.writeString(staging.resolve(
                            base + "-build-manifest.json"),
                    manifest, StandardCharsets.UTF_8);
            move(staging, output);
            published = true;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
        System.out.println("Distribution published: " + output);
    }

    private static Inspection inspect(final Path root, final Path jarPath)
            throws IOException {
        final Contract contract = Contract.read(root.resolve(CONTRACT));
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalStateException(
                    "Analyzer JAR is unavailable: " + jarPath);
        }
        final String pluginBase = PLUGIN_PREFIX + contract.pluginVersion;
        final String pluginJarName = pluginBase + ".jar";
        final String pluginPomName = pluginBase + ".pom";
        byte[] pluginJar = null;
        String pluginChecksum = null;
        String pluginPom = null;
        String pluginPomChecksum = null;
        final Properties metadata = new Properties();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            if (!"io.github.dependencyanalysis.cli.DependencyAnalyzerCli"
                    .equals(jar.getManifest().getMainAttributes()
                            .getValue("Main-Class"))) {
                throw new IllegalStateException(
                        "Analyzer Main-Class is invalid");
            }
            final JarEntry metadataEntry = required(jar,
                    "META-INF/dependency-analyzer-build.properties");
            try (InputStream input = jar.getInputStream(metadataEntry)) {
                metadata.load(input);
            }
            pluginJar = bytes(jar, required(jar, pluginJarName));
            pluginChecksum = text(jar,
                    required(jar, pluginJarName + ".sha512")).trim();
            pluginPom = text(jar, required(jar, pluginPomName));
            pluginPomChecksum = text(jar,
                    required(jar, pluginPomName + ".sha512")).trim();
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (name.startsWith(PLUGIN_PREFIX)
                        && !name.equals(pluginJarName)
                        && !name.equals(pluginJarName + ".sha512")
                        && !name.equals(pluginPomName)
                        && !name.equals(pluginPomName + ".sha512")) {
                    throw new IllegalStateException(
                            "Unexpected embedded Plugin resource: " + name);
                }
            }
        }
        final String analyzerVersion = metadata.getProperty(
                "analyzer.version", "").trim();
        final String pluginVersion = metadata.getProperty(
                "artifact-path-plugin.version", "").trim();
        if (!contract.analyzerVersion.equals(analyzerVersion)
                || !contract.pluginVersion.equals(pluginVersion)) {
            throw new IllegalStateException(
                    "Packaged build metadata does not match version contract");
        }
        if (!pluginPom.contains("<version>"
                + contract.pluginVersion + "</version>")) {
            throw new IllegalStateException(
                    "Consumer POM version does not match version contract");
        }
        final String embeddedPluginSha512 = sha512(pluginJar);
        if (!embeddedPluginSha512.equals(pluginChecksum)) {
            throw new IllegalStateException(
                    "Embedded Plugin checksum mismatch");
        }
        if (!sha512(pluginPom.getBytes(StandardCharsets.UTF_8))
                .equals(pluginPomChecksum)) {
            throw new IllegalStateException(
                    "Embedded Plugin consumer POM checksum mismatch");
        }
        inspectPlugin(pluginJar);
        return new Inspection(analyzerVersion, pluginVersion,
                contract.outputTimestamp, sha512(jarPath),
                embeddedPluginSha512);
    }

    private static void inspectPlugin(final byte[] pluginJar)
            throws IOException {
        boolean descriptorFound = false;
        boolean relocatedJackson = false;
        try (JarInputStream jar = new JarInputStream(
                new ByteArrayInputStream(pluginJar))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                final String name = entry.getName();
                if ("META-INF/maven/plugin.xml".equals(name)) {
                    final String descriptor = new String(
                            readCurrentEntry(jar), StandardCharsets.UTF_8);
                    descriptorFound = descriptor.contains(
                            "<name>dependencyGraphFileName</name>")
                            && descriptor.contains(
                            "${cia.dependencyGraphFileName}");
                }
                if (name.startsWith(
                        "io/github/dependencyanalysis/maven/internal/jackson/")) {
                    relocatedJackson = true;
                }
                if (name.startsWith("org/apache/maven/")
                        || name.startsWith("org/eclipse/aether/")
                        || name.startsWith("com/fasterxml/jackson/core/")) {
                    throw new IllegalStateException(
                            "Forbidden Plugin implementation class: " + name);
                }
                if (name.endsWith(".class")
                        && !name.startsWith("META-INF/versions/")) {
                    final DataInputStream data = new DataInputStream(jar);
                    if (data.readInt() != CLASS_MAGIC) {
                        throw new IllegalStateException(
                                "Invalid class entry: " + name);
                    }
                    data.readUnsignedShort();
                    final int major = data.readUnsignedShort();
                    if (major > JAVA_EIGHT_MAJOR) {
                        throw new IllegalStateException(
                                "Plugin class requires Java major " + major
                                        + ": " + name);
                    }
                }
                jar.closeEntry();
            }
        }
        if (!descriptorFound || !relocatedJackson) {
            throw new IllegalStateException(
                    "Plugin descriptor or relocated Jackson is unavailable");
        }
    }

    private static String manifest(final Inspection inspection,
                                   final boolean eligible,
                                   final boolean dirty,
                                   final String commit,
                                   final String javaVersion,
                                   final String mavenVersion) {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"releaseEligible\": " + eligible + ",\n"
                + "  \"analyzerVersion\": \""
                + json(inspection.analyzerVersion) + "\",\n"
                + "  \"artifactPathPluginVersion\": \""
                + json(inspection.pluginVersion) + "\",\n"
                + "  \"gitCommit\": \"" + json(commit) + "\",\n"
                + "  \"gitDirty\": " + dirty + ",\n"
                + "  \"outputTimestamp\": \""
                + json(inspection.outputTimestamp) + "\",\n"
                + "  \"javaVersion\": \"" + json(javaVersion) + "\",\n"
                + "  \"mavenVersion\": \"" + json(mavenVersion) + "\",\n"
                + "  \"artifactSha512\": \""
                + inspection.artifactSha512 + "\",\n"
                + "  \"embeddedPluginSha512\": \""
                + inspection.embeddedPluginSha512 + "\"\n"
                + "}\n";
    }

    private static String json(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(Locale.ROOT,
                                "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    private static JarEntry required(final JarFile jar, final String name) {
        final JarEntry entry = jar.getJarEntry(name);
        if (entry == null) {
            throw new IllegalStateException(
                    "Packaged resource is unavailable: " + name);
        }
        return entry;
    }

    private static byte[] bytes(final JarFile jar, final JarEntry entry)
            throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static String text(final JarFile jar, final JarEntry entry)
            throws IOException {
        return new String(bytes(jar, entry), StandardCharsets.UTF_8);
    }

    private static byte[] readCurrentEntry(final JarInputStream input)
            throws IOException {
        final byte[] buffer = new byte[8192];
        final java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String sha512(final Path path) throws IOException {
        return sha512(Files.readAllBytes(path));
    }

    private static String sha512(final byte[] bytes) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-512 is unavailable", exception);
        }
        final byte[] value = digest.digest(bytes);
        final StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return result.toString();
    }

    private static void move(final Path source, final Path destination)
            throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Inspection(String analyzerVersion,
                              String pluginVersion,
                              String outputTimestamp,
                              String artifactSha512,
                              String embeddedPluginSha512) {
    }

    private record Contract(String analyzerVersion,
                            String pluginVersion,
                            String outputTimestamp) {
        static Contract read(final Path path) throws IOException {
            final Properties values = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            }
            final String analyzer = required(values,
                    "current.analyzer.version");
            final String plugin = required(values,
                    "current.artifact-path-plugin.version");
            final String timestamp = required(values,
                    "analyzer." + analyzer + ".outputTimestamp");
            return new Contract(analyzer, plugin, timestamp);
        }

        private static String required(final Properties values,
                                       final String key) {
            final String value = values.getProperty(key, "").trim();
            if (value.isEmpty()) {
                throw new IllegalStateException(
                        "Missing version contract property: " + key);
            }
            return value;
        }
    }

    private static final class Options {
        private final Map<String, String> values;

        private Options(final Map<String, String> options) {
            values = options;
        }

        static Options parse(final String[] arguments, final int start) {
            final Map<String, String> options = new LinkedHashMap<>();
            for (int index = start; index < arguments.length; index += 2) {
                if (index + 1 >= arguments.length) {
                    throw new IllegalArgumentException(
                            "Missing value for " + arguments[index]);
                }
                if (options.put(arguments[index], arguments[index + 1])
                        != null) {
                    throw new IllegalArgumentException(
                            "Duplicate option: " + arguments[index]);
                }
            }
            return new Options(options);
        }

        Path root() {
            return requiredPath("--root").toAbsolutePath().normalize();
        }

        Path jar() {
            return requiredPath("--jar").toAbsolutePath().normalize();
        }

        Path requiredPath(final String name) {
            return Paths.get(required(name));
        }

        String required(final String name) {
            final String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing option: " + name);
            }
            return value;
        }

        boolean requiredBoolean(final String name) {
            final String value = required(name);
            if (!"true".equals(value) && !"false".equals(value)) {
                throw new IllegalArgumentException(
                        "Expected true or false for " + name);
            }
            return Boolean.parseBoolean(value);
        }
    }
}
