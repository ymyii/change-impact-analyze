package scripts.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Wiki: wiki/rules/release-versioning.md - Canonical SemVer and fingerprint implementation
// Wiki: wiki/runbooks/version-and-distribution.md - Version command backend
/** Source-file Java 17 helper for the tracked release version contract. */
public final class VersionTool {

    private static final String CONTRACT =
            "build-support/version-contract.properties";
    private static final String ANALYZER = "analyzer";
    private static final String PLUGIN = "artifact-path-plugin";
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$");
    private static final Pattern REVISION = Pattern.compile(
            "(<revision>)([^<]+)(</revision>)");
    private static final Pattern ROOT_PLUGIN_VERSION = Pattern.compile(
            "(<artifact-path-plugin\\.version>)([^<]+)"
                    + "(</artifact-path-plugin\\.version>)");
    private static final Pattern PLUGIN_PROJECT_VERSION = Pattern.compile(
            "(<artifactId>dependency-analyzer-artifact-path-maven-plugin"
                    + "</artifactId>\\s*<version>)([^<]+)(</version>)");
    private static final Pattern OUTPUT_TIMESTAMP = Pattern.compile(
            "(<project\\.build\\.outputTimestamp>)([^<]+)"
                    + "(</project\\.build\\.outputTimestamp>)");
    private static final Pattern VERSION_BLOCK = Pattern.compile(
            "(?s)<!-- version-contract:start -->.*?"
                    + "<!-- version-contract:end -->");
    private static final List<String> VERSION_DOCUMENTS = List.of(
            "docs/user-manual.md",
            "wiki/project/dependency-analyzer.md",
            "wiki/features/maven-runtime.md",
            "wiki/rules/release-versioning.md",
            "wiki/runbooks/build-test-package.md",
            "wiki/runbooks/version-and-distribution.md");

    private final Path root;

    private VersionTool(final Path repositoryRoot) {
        root = repositoryRoot.toAbsolutePath().normalize();
    }

    /** Executes the command. */
    public static void main(final String[] arguments) {
        try {
            final Arguments parsed = Arguments.parse(arguments);
            final VersionTool tool = new VersionTool(parsed.root);
            tool.execute(parsed.commandArguments);
        } catch (IllegalArgumentException | IllegalStateException
                 | IOException exception) {
            System.err.println("version: " + exception.getMessage());
            System.exit(1);
        }
    }

    private void execute(final List<String> arguments) throws IOException {
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected show, verify, fingerprints, or bump");
        }
        final String command = arguments.get(0);
        switch (command) {
            case "show" -> show();
            case "verify" -> verify(true);
            case "fingerprints" -> fingerprints();
            case "self-test" -> selfTest();
            case "bump" -> bump(arguments.subList(1, arguments.size()));
            default -> throw new IllegalArgumentException(
                    "Unknown command: " + command);
        }
    }

    private void selfTest() {
        final SemVer version = SemVer.parse("1.2.3");
        require("2.0.0".equals(version.bump(Level.MAJOR).toString()),
                "major bump");
        require("1.3.0".equals(version.bump(Level.MINOR).toString()),
                "minor bump");
        require("1.2.4".equals(version.bump(Level.PATCH).toString()),
                "patch bump");
        boolean snapshotRejected = false;
        try {
            SemVer.parse("1.2.3-SNAPSHOT");
        } catch (IllegalArgumentException exception) {
            snapshotRejected = true;
        }
        require(snapshotRejected, "pre-release rejection");
        boolean pluginOnlyRejected = false;
        try {
            final BumpRequest request = BumpRequest.parse(
                    List.of("--plugin", "patch"));
            if (request.pluginLevel != null
                    && request.analyzerLevel == null) {
                throw new IllegalArgumentException("plugin-only bump");
            }
        } catch (IllegalArgumentException exception) {
            pluginOnlyRejected = true;
        }
        require(pluginOnlyRejected, "plugin-only rejection");
        System.out.println("Version tool self-test passed");
    }

    private void require(final boolean condition, final String label) {
        if (!condition) {
            throw new IllegalStateException(
                    "Version tool self-test failed: " + label);
        }
    }

    private void show() throws IOException {
        final Contract contract = Contract.read(root.resolve(CONTRACT));
        System.out.println("Analyzer " + contract.analyzerVersion());
        System.out.println("Artifact Path Plugin "
                + contract.pluginVersion());
    }

    private void fingerprints() throws IOException {
        final Contract contract = Contract.read(root.resolve(CONTRACT));
        assertPomVersions(contract);
        System.out.println("analyzer.inputs.sha512="
                + analyzerFingerprint());
        System.out.println("artifact-path-plugin.inputs.sha512="
                + pluginFingerprint());
    }

    private Contract verify(final boolean print) throws IOException {
        final Contract contract = Contract.read(root.resolve(CONTRACT));
        assertPomVersions(contract);
        assertTemplateContracts();
        assertDocumentationVersions(contract);
        contract.assertCurrentIsLatest();
        final String analyzerFingerprint = analyzerFingerprint();
        final String pluginFingerprint = pluginFingerprint();
        if (!contract.analyzerFingerprint()
                .equals(analyzerFingerprint)) {
            throw new IllegalStateException(
                    "Analyzer inputs changed without an Analyzer version "
                            + "bump: expected="
                            + contract.analyzerFingerprint()
                            + "; actual=" + analyzerFingerprint);
        }
        if (!contract.pluginFingerprint().equals(pluginFingerprint)) {
            throw new IllegalStateException(
                    "Artifact Path Plugin inputs changed without a Plugin "
                            + "version bump: expected="
                            + contract.pluginFingerprint()
                            + "; actual=" + pluginFingerprint);
        }
        if (print) {
            System.out.println("Version contract verified: Analyzer "
                    + contract.analyzerVersion()
                    + "; Artifact Path Plugin "
                    + contract.pluginVersion());
        }
        return contract;
    }

    private void bump(final List<String> arguments) throws IOException {
        final BumpRequest request = BumpRequest.parse(arguments);
        final Contract current = Contract.read(root.resolve(CONTRACT));
        assertPomVersions(current);
        assertTemplateContracts();
        assertDocumentationVersions(current);
        current.assertCurrentIsLatest();

        final boolean analyzerChanged = !current.analyzerFingerprint()
                .equals(analyzerFingerprint());
        final boolean pluginChanged = !current.pluginFingerprint()
                .equals(pluginFingerprint());
        if (pluginChanged && request.pluginLevel == null) {
            throw new IllegalStateException(
                    "Plugin inputs changed; --plugin bump is required");
        }
        if ((analyzerChanged || pluginChanged)
                && request.analyzerLevel == null) {
            throw new IllegalStateException(
                    "Distribution inputs changed; --analyzer bump is required");
        }
        if (request.pluginLevel != null
                && request.analyzerLevel == null) {
            throw new IllegalArgumentException(
                    "--plugin requires a simultaneous --analyzer bump");
        }

        final String analyzerVersion = request.analyzerLevel == null
                ? current.analyzerVersion()
                : SemVer.parse(current.analyzerVersion())
                .bump(request.analyzerLevel).toString();
        final String pluginVersion = request.pluginLevel == null
                ? current.pluginVersion()
                : SemVer.parse(current.pluginVersion())
                .bump(request.pluginLevel).toString();
        final String timestamp = request.analyzerLevel == null
                ? current.outputTimestamp()
                : Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

        final Map<Path, String> originals = new LinkedHashMap<>();
        final Map<Path, String> replacements = new LinkedHashMap<>();
        final Path rootPom = root.resolve("pom.xml");
        final Path pluginPom = root.resolve(
                "plugins/artifact-path-resolver/pom.xml");
        stageTextChange(rootPom, originals, replacements,
                replaceGroup(read(rootPom), REVISION, analyzerVersion,
                        "root revision"));
        stageTextChange(rootPom, originals, replacements,
                replaceGroup(replacements.get(rootPom),
                        ROOT_PLUGIN_VERSION, pluginVersion,
                        "root Artifact Path Plugin version"));
        stageTextChange(rootPom, originals, replacements,
                replaceGroup(replacements.get(rootPom), OUTPUT_TIMESTAMP,
                        timestamp, "output timestamp"));
        stageTextChange(pluginPom, originals, replacements,
                replaceGroup(read(pluginPom), PLUGIN_PROJECT_VERSION,
                        pluginVersion, "Plugin project version"));

        final Contract next = current.withCurrent(
                analyzerVersion, pluginVersion, timestamp);
        try {
            writeStaged(replacements);
            final Contract sealed = next.withFingerprints(
                    analyzerFingerprint(), pluginFingerprint());
            replacements.put(root.resolve(CONTRACT), sealed.serialize());
            stageDocumentationVersions(
                    analyzerVersion, pluginVersion,
                    originals, replacements);
            atomicReplace(replacements, originals);
        } catch (IOException | RuntimeException exception) {
            restore(originals);
            throw exception;
        }
        verify(false);
        System.out.println("Version contract bumped: Analyzer "
                + analyzerVersion + "; Artifact Path Plugin "
                + pluginVersion);
    }

    private void assertPomVersions(final Contract contract)
            throws IOException {
        final String rootPom = read(root.resolve("pom.xml"));
        final String pluginPom = read(root.resolve(
                "plugins/artifact-path-resolver/pom.xml"));
        assertValue(rootPom, REVISION, contract.analyzerVersion(),
                "root revision");
        assertValue(rootPom, ROOT_PLUGIN_VERSION,
                contract.pluginVersion(), "root Plugin version");
        assertValue(pluginPom, PLUGIN_PROJECT_VERSION,
                contract.pluginVersion(), "Plugin project version");
        assertValue(rootPom, OUTPUT_TIMESTAMP,
                contract.outputTimestamp(), "output timestamp");
    }

    private void assertTemplateContracts() throws IOException {
        for (String pom : List.of(
                "analyzer/pom.xml",
                "plugins/pom.xml",
                "plugins/artifact-path-resolver/pom.xml")) {
            if (!read(root.resolve(pom)).contains(
                    "<version>${revision}</version>")) {
                throw new IllegalStateException(
                        "Module parent version must use ${revision}: "
                                + pom);
            }
        }
        final String metadata = read(root.resolve(
                "analyzer/src/main/filtered-resources/META-INF/"
                        + "dependency-analyzer-build.properties"));
        if (!metadata.contains("analyzer.version=@revision@")
                || !metadata.contains("artifact-path-plugin.version="
                + "@artifact-path-plugin.version@")) {
            throw new IllegalStateException(
                    "Filtered build metadata placeholders are invalid");
        }
        final String pom = read(root.resolve(
                "build-support/artifact-path-plugin-consumer.pom.template"));
        if (!pom.contains("<version>"
                + "@ARTIFACT_PATH_PLUGIN_VERSION@</version>")) {
            throw new IllegalStateException(
                    "Consumer POM version placeholder is invalid");
        }
    }

    private void assertDocumentationVersions(final Contract contract)
            throws IOException {
        final String expected = versionBlock(contract.analyzerVersion(),
                contract.pluginVersion());
        for (String document : VERSION_DOCUMENTS) {
            final String content = read(root.resolve(document));
            final Matcher matcher = VERSION_BLOCK.matcher(content);
            if (!matcher.find() || !expected.equals(matcher.group())
                    || matcher.find()) {
                throw new IllegalStateException(
                        "Documentation version marker does not match "
                                + "version contract: " + document);
            }
        }
    }

    private String analyzerFingerprint() throws IOException {
        final List<Path> inputs = new ArrayList<>();
        inputs.add(root.resolve("pom.xml"));
        inputs.add(root.resolve("analyzer/pom.xml"));
        collectFiles(root.resolve("analyzer/src/main"), inputs);
        return fingerprint(inputs);
    }

    private String pluginFingerprint() throws IOException {
        final List<Path> inputs = new ArrayList<>();
        inputs.add(root.resolve("plugins/pom.xml"));
        inputs.add(root.resolve("plugins/artifact-path-resolver/pom.xml"));
        inputs.add(root.resolve(
                "build-support/artifact-path-plugin-consumer.pom.template"));
        collectFiles(root.resolve(
                "plugins/artifact-path-resolver/src/main"), inputs);
        return fingerprint(inputs);
    }

    private String fingerprint(final List<Path> inputs) throws IOException {
        inputs.sort(Comparator.comparing(path -> root.relativize(path)
                .toString().replace('\\', '/')));
        final MessageDigest digest = sha512();
        for (Path input : inputs) {
            if (!Files.isRegularFile(input)) {
                throw new IllegalStateException(
                        "Fingerprint input is unavailable: " + input);
            }
            final String relative = root.relativize(input).toString()
                    .replace('\\', '/');
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(input));
            digest.update((byte) 0);
        }
        return hex(digest.digest());
    }

    private void collectFiles(final Path directory,
                              final List<Path> inputs) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "Fingerprint directory is unavailable: " + directory);
        }
        try (var stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile).forEach(inputs::add);
        }
    }

    private void stageDocumentationVersions(
            final String analyzerVersion,
            final String pluginVersion,
            final Map<Path, String> originals,
            final Map<Path, String> replacements) throws IOException {
        final String block = versionBlock(analyzerVersion, pluginVersion);
        for (String directoryName : List.of("docs", "wiki")) {
            final Path directory = root.resolve(directoryName);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var stream = Files.walk(directory)) {
                for (Path path : stream.filter(value -> Files.isRegularFile(value)
                        && value.getFileName().toString().endsWith(".md"))
                        .toList()) {
                    final String current = read(path);
                    final Matcher matcher = VERSION_BLOCK.matcher(current);
                    if (matcher.find()) {
                        stageTextChange(path, originals, replacements,
                                matcher.replaceAll(Matcher.quoteReplacement(
                                        block)));
                    }
                }
            }
        }
    }

    private String versionBlock(final String analyzerVersion,
                                final String pluginVersion) {
        return "<!-- version-contract:start -->\n"
                + "- Analyzer release: `" + analyzerVersion + "`\n"
                + "- Artifact Path Plugin release: `" + pluginVersion + "`\n"
                + "<!-- version-contract:end -->";
    }

    private void stageTextChange(
            final Path path,
            final Map<Path, String> originals,
            final Map<Path, String> replacements,
            final String replacement) throws IOException {
        originals.putIfAbsent(path, read(path));
        replacements.put(path, replacement);
    }

    private void writeStaged(final Map<Path, String> replacements)
            throws IOException {
        for (Map.Entry<Path, String> entry : replacements.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue(),
                    StandardCharsets.UTF_8);
        }
    }

    private void atomicReplace(
            final Map<Path, String> replacements,
            final Map<Path, String> originals) throws IOException {
        final Map<Path, Path> temporaryFiles = new LinkedHashMap<>();
        try {
            for (Map.Entry<Path, String> entry : replacements.entrySet()) {
                originals.putIfAbsent(entry.getKey(), read(entry.getKey()));
                final Path temporary = Files.createTempFile(
                        entry.getKey().getParent(),
                        entry.getKey().getFileName().toString(), ".tmp");
                Files.writeString(temporary, entry.getValue(),
                        StandardCharsets.UTF_8);
                temporaryFiles.put(entry.getKey(), temporary);
            }
            for (Map.Entry<Path, Path> entry : temporaryFiles.entrySet()) {
                move(entry.getValue(), entry.getKey());
            }
        } catch (IOException exception) {
            restore(originals);
            throw exception;
        } finally {
            for (Path temporary : temporaryFiles.values()) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void restore(final Map<Path, String> originals) {
        for (Map.Entry<Path, String> entry : originals.entrySet()) {
            try {
                Files.writeString(entry.getKey(), entry.getValue(),
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                System.err.println("version: unable to restore "
                        + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private void move(final Path source, final Path destination)
            throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String read(final Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalStateException(
                    "Path escapes repository root: " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String replaceGroup(final String input,
                                final Pattern pattern,
                                final String value,
                                final String label) {
        final Matcher matcher = pattern.matcher(input);
        if (!matcher.find() || matcher.find()) {
            throw new IllegalStateException(
                    "Expected exactly one " + label);
        }
        matcher.reset();
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Expected exactly one " + label);
        }
        return input.substring(0, matcher.start())
                + matcher.group(1) + value + matcher.group(3)
                + input.substring(matcher.end());
    }

    private void assertValue(final String input,
                             final Pattern pattern,
                             final String expected,
                             final String label) {
        final Matcher matcher = pattern.matcher(input);
        if (!matcher.find() || !expected.equals(matcher.group(2))
                || matcher.find()) {
            throw new IllegalStateException(
                    label + " does not match version contract");
        }
    }

    private static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-512 is unavailable", exception);
        }
    }

    private static String hex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private record Arguments(Path root, List<String> commandArguments) {
        static Arguments parse(final String[] arguments) {
            if (arguments.length < 3 || !"--root".equals(arguments[0])) {
                throw new IllegalArgumentException(
                        "Expected --root <repository> <command>");
            }
            return new Arguments(Paths.get(arguments[1]),
                    List.copyOf(Arrays.asList(arguments)
                            .subList(2, arguments.length)));
        }
    }

    private enum Level {
        MAJOR,
        MINOR,
        PATCH;

        static Level parse(final String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Expected major, minor, or patch: " + value);
            }
        }
    }

    private record SemVer(int major, int minor, int patch)
            implements Comparable<SemVer> {
        static SemVer parse(final String value) {
            final Matcher matcher = SEMVER.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Version must be strict MAJOR.MINOR.PATCH: " + value);
            }
            return new SemVer(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        }

        SemVer bump(final Level level) {
            return switch (level) {
                case MAJOR -> new SemVer(major + 1, 0, 0);
                case MINOR -> new SemVer(major, minor + 1, 0);
                case PATCH -> new SemVer(major, minor, patch + 1);
            };
        }

        @Override
        public int compareTo(final SemVer other) {
            int comparison = Integer.compare(major, other.major);
            if (comparison == 0) {
                comparison = Integer.compare(minor, other.minor);
            }
            if (comparison == 0) {
                comparison = Integer.compare(patch, other.patch);
            }
            return comparison;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    private record BumpRequest(Level analyzerLevel, Level pluginLevel) {
        static BumpRequest parse(final List<String> arguments) {
            Level analyzer = null;
            Level plugin = null;
            for (int index = 0; index < arguments.size(); index += 2) {
                if (index + 1 >= arguments.size()) {
                    throw new IllegalArgumentException(
                            "Missing bump level for " + arguments.get(index));
                }
                final String option = arguments.get(index);
                final Level level = Level.parse(arguments.get(index + 1));
                if ("--analyzer".equals(option) && analyzer == null) {
                    analyzer = level;
                } else if ("--plugin".equals(option) && plugin == null) {
                    plugin = level;
                } else {
                    throw new IllegalArgumentException(
                            "Duplicate or unknown option: " + option);
                }
            }
            if (analyzer == null && plugin == null) {
                throw new IllegalArgumentException(
                        "At least one bump is required");
            }
            return new BumpRequest(analyzer, plugin);
        }
    }

    private static final class Contract {
        private final LinkedHashMap<String, String> values;

        private Contract(final LinkedHashMap<String, String> entries) {
            values = entries;
        }

        static Contract read(final Path path) throws IOException {
            final LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path,
                    StandardCharsets.UTF_8)) {
                final String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                final int separator = value.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalStateException(
                            "Invalid version contract line: " + line);
                }
                final String key = value.substring(0, separator).trim();
                final String property = value.substring(separator + 1).trim();
                if (entries.put(key, property) != null) {
                    throw new IllegalStateException(
                            "Duplicate version contract property: " + key);
                }
            }
            final Contract contract = new Contract(entries);
            if (!"1".equals(contract.required("schema.version"))) {
                throw new IllegalStateException(
                        "Unsupported version contract schema");
            }
            SemVer.parse(contract.analyzerVersion());
            SemVer.parse(contract.pluginVersion());
            Instant.parse(contract.outputTimestamp());
            contract.analyzerFingerprint();
            contract.pluginFingerprint();
            return contract;
        }

        String analyzerVersion() {
            return required("current.analyzer.version");
        }

        String pluginVersion() {
            return required("current.artifact-path-plugin.version");
        }

        String analyzerFingerprint() {
            return required(ANALYZER + "." + analyzerVersion()
                    + ".inputs.sha512");
        }

        String pluginFingerprint() {
            return required(PLUGIN + "." + pluginVersion()
                    + ".inputs.sha512");
        }

        String outputTimestamp() {
            return required(ANALYZER + "." + analyzerVersion()
                    + ".outputTimestamp");
        }

        void assertCurrentIsLatest() {
            assertLatest(ANALYZER, analyzerVersion());
            assertLatest(PLUGIN, pluginVersion());
        }

        Contract withCurrent(final String analyzerVersion,
                             final String pluginVersion,
                             final String timestamp) {
            final LinkedHashMap<String, String> next =
                    new LinkedHashMap<>(values);
            next.put("current.analyzer.version", analyzerVersion);
            next.put("current.artifact-path-plugin.version", pluginVersion);
            next.put(ANALYZER + "." + analyzerVersion
                    + ".outputTimestamp", timestamp);
            return new Contract(next);
        }

        Contract withFingerprints(final String analyzerFingerprint,
                                  final String pluginFingerprint) {
            final LinkedHashMap<String, String> next =
                    new LinkedHashMap<>(values);
            final String analyzerKey = ANALYZER + "." + analyzerVersion()
                    + ".inputs.sha512";
            final String pluginKey = PLUGIN + "." + pluginVersion()
                    + ".inputs.sha512";
            rejectReuse(next, analyzerKey, analyzerFingerprint);
            rejectReuse(next, pluginKey, pluginFingerprint);
            next.put(analyzerKey, analyzerFingerprint);
            next.put(pluginKey, pluginFingerprint);
            return new Contract(next);
        }

        String serialize() {
            final StringBuilder result = new StringBuilder();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result.append(entry.getKey()).append('=')
                        .append(entry.getValue()).append('\n');
            }
            return result.toString();
        }

        private void assertLatest(final String prefix,
                                  final String currentVersion) {
            final SemVer current = SemVer.parse(currentVersion);
            SemVer latest = current;
            for (String key : values.keySet()) {
                final String start = prefix + ".";
                final String end = ".inputs.sha512";
                if (key.startsWith(start) && key.endsWith(end)) {
                    final String value = key.substring(start.length(),
                            key.length() - end.length());
                    final SemVer candidate = SemVer.parse(value);
                    if (candidate.compareTo(latest) > 0) {
                        latest = candidate;
                    }
                }
            }
            if (!current.equals(latest)) {
                throw new IllegalStateException(
                        prefix + " version rollback is forbidden: current="
                                + current + "; latest=" + latest);
            }
        }

        private String required(final String key) {
            final String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Missing version contract property: " + key);
            }
            if (key.endsWith(".sha512")
                    && !value.matches("[0-9a-f]{128}")) {
                throw new IllegalStateException(
                        "Invalid SHA-512 in version contract: " + key);
            }
            return value;
        }

        private static void rejectReuse(
                final Map<String, String> entries,
                final String key,
                final String fingerprint) {
            final String previous = entries.get(key);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IllegalStateException(
                        "Release version reuse is forbidden: " + key);
            }
        }
    }
}
