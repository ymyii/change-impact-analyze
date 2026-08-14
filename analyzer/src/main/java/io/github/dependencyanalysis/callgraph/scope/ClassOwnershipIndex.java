package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Wiki: wiki/features/call-graph-engine.md - Canonical classpath winner policy
/** Binary-name ownership index with deterministic duplicate resolution. */
public final class ClassOwnershipIndex {

    /** Java Module Descriptor class entry. */
    private static final String MODULE_INFO_CLASS = "module-info.class";

    /** Multi-release JAR class entry prefix. */
    private static final String MULTI_RELEASE_PREFIX =
            "META-INF/versions/";

    /** SHA-256 algorithm name. */
    private static final String SHA_256 = "SHA-256";

    /** Lowest ownership priority. */
    private static final int FALLBACK_PRIORITY = 4;

    /** External dependency ownership priority. */
    private static final int DEPENDENCY_PRIORITY = 3;

    /** Indexed classes. */
    private final Map<String, ClassOwnership> classes = new HashMap<>();

    /** Repeated definitions retained only for duplicate evidence. */
    private final Map<String, List<ClassOwnership>> repeated =
            new LinkedHashMap<>();

    /**
     * Adds every class under one classes directory.
     *
     * @param directory classes directory
     * @param origin code origin
     * @throws IOException on unreadable class
     */
    public void addDirectory(
            final Path directory, final CodeOrigin origin)
            throws IOException {
        final List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !isModuleInfoClass(
                            directory.relativize(path).toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            final String name = normalizeClassName(
                    directory.relativize(file).toString());
            add(name, origin, ClassSource.path(directory),
                    digest(Files.readAllBytes(file)));
        }
    }

    /**
     * Adds every Java 8-visible class from one JAR.
     *
     * @param jarPath JAR path
     * @param origin code origin
     * @throws IOException on unreadable entry
     */
    public void addJar(final Path jarPath, final CodeOrigin origin)
            throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            addJar(jar, ClassSource.path(jarPath), origin);
        }
    }

    /**
     * Adds every Java 8-visible class from one dependency JAR.
     *
     * @param coordinate logical dependency source
     * @param jar open JAR handle
     * @param origin code origin
     * @throws IOException on unreadable entry
     */
    public void addJar(
            final ArtifactCoord coordinate,
            final JarFile jar,
            final CodeOrigin origin) throws IOException {
        addJar(jar, ClassSource.artifact(coordinate), origin);
    }

    private void addJar(
            final JarFile jar,
            final ClassSource source,
            final CodeOrigin origin) throws IOException {
        final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !isModuleInfoClass(entry.getName()))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    add(normalizeClassName(entry.getName()), origin,
                            source, digest(input.readAllBytes()));
                }
            }
    }

    /**
     * Resolves an additional JAR against already indexed classes without
     * retaining its non-conflicting entries. This keeps JDK duplicate checks
     * cheap while preserving parent-loader precedence.
     *
     * @param jarPath additional JAR
     * @param origin additional code origin
     * @param includedNames internal names to validate
     * @throws IOException on unreadable conflicting entry
     */
    public void validateJarDuplicates(
            final Path jarPath,
            final CodeOrigin origin,
            final Predicate<String> includedNames) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !isModuleInfoClass(entry.getName()))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                final String name = normalizeClassName(entry.getName());
                if (!classes.containsKey(name)
                        || !includedNames.test(name)) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    add(name, origin, ClassSource.path(jarPath),
                            digest(input.readAllBytes()));
                }
            }
        }
    }

    private void add(
            final String name,
            final CodeOrigin origin,
            final ClassSource source,
            final String digest) {
        final ClassOwnership previous = classes.get(name);
        final ClassOwnership candidate = new ClassOwnership(
                origin, source, digest);
        if (previous != null) {
            final List<ClassOwnership> definitions = repeated
                    .computeIfAbsent(name, ignored -> {
                        final List<ClassOwnership> values =
                                new ArrayList<>();
                        values.add(previous);
                        return values;
                    });
            if (!contains(definitions, candidate)) {
                definitions.add(candidate);
            }
        }
        if (previous == null || priority(origin)
                < priority(previous.getOrigin())) {
            classes.put(name, candidate);
        }
    }

    private boolean contains(
            final List<ClassOwnership> values,
            final ClassOwnership candidate) {
        return values.stream().anyMatch(value ->
                value.getOrigin() == candidate.getOrigin()
                        && value.getSource().equals(candidate.getSource())
                        && value.getDigest().equals(candidate.getDigest()));
    }

    /**
     * Returns ownership for a WALA-style or internal binary name.
     *
     * @param name class name
     * @return ownership, or null for JDK/unindexed classes
     */
    public ClassOwnership ownershipOf(final String name) {
        return classes.get(normalizedLookupName(name));
    }

    /** @return indexed binary names */
    public List<String> binaryNames() {
        final List<String> result = new ArrayList<>(classes.keySet());
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    /**
     * Returns content-conflicting duplicate resolutions.
     *
     * @return stable binary-name ordered resolution evidence
     */
    public List<DuplicateClassResolution> duplicateClassResolutions() {
        final List<DuplicateClassResolution> result = new ArrayList<>();
        for (String name : repeated.keySet()) {
            final DuplicateClassResolution resolution = resolution(name);
            if (resolution != null) {
                result.add(resolution);
            }
        }
        result.sort(Comparator.comparing(
                DuplicateClassResolution::getBinaryName));
        return List.copyOf(result);
    }

    /**
     * Returns conflict evidence for one name.
     *
     * @param name WALA-style or internal binary name
     * @return resolution, or null when the name is not content-conflicting
     */
    public DuplicateClassResolution duplicateResolutionOf(
            final String name) {
        return resolution(normalizedLookupName(name));
    }

    /**
     * Tests whether one source owns the effective definition.
     * Unindexed names are allowed for JDK-only classes and resources.
     *
     * @param name WALA-style or internal binary name
     * @param source physical classpath source
     * @return true when the entry should be exposed
     */
    public boolean isEffectiveDefinition(
            final String name,
            final Path source) {
        return isEffectiveDefinition(name, ClassSource.path(source));
    }

    /**
     * Tests whether one logical source owns the effective definition.
     *
     * @param name WALA-style or internal binary name
     * @param source logical classpath source
     * @return true when the entry should be exposed
     */
    public boolean isEffectiveDefinition(
            final String name,
            final ClassSource source) {
        final String normalized = normalizedLookupName(name);
        if ("module-info".equals(normalized)
                || normalized.startsWith(MULTI_RELEASE_PREFIX)) {
            return false;
        }
        final ClassOwnership ownership = classes.get(normalized);
        return ownership == null || ownership.getSource().equals(source);
    }

    private DuplicateClassResolution resolution(final String name) {
        final List<ClassOwnership> definitions = repeated.get(name);
        if (definitions == null) {
            return null;
        }
        final Set<String> digests = new HashSet<>();
        definitions.forEach(value -> digests.add(value.getDigest()));
        if (digests.size() < 2) {
            return null;
        }
        final ClassOwnership winner = classes.get(name);
        return new DuplicateClassResolution(name, winner, definitions,
                precedenceReason(winner));
    }

    private static String precedenceReason(
            final ClassOwnership winner) {
        return switch (winner.getOrigin()) {
            case JDK -> "JDK parent/bootstrap precedence";
            case PROJECT -> "Current module target/classes precedence";
            case REACTOR_DEPENDENCY ->
                    "Reactor dependency classpath order";
            case DEPENDENCY -> "External dependency classpath order";
            default -> "Classpath discovery order";
        };
    }

    private static int priority(final CodeOrigin origin) {
        return switch (origin) {
            case JDK -> 0;
            case PROJECT -> 1;
            case REACTOR_DEPENDENCY -> 2;
            case DEPENDENCY -> DEPENDENCY_PRIORITY;
            default -> FALLBACK_PRIORITY;
        };
    }

    private static String normalizeClassName(final String value) {
        final String unix = value.replace('\\', '/');
        return unix.substring(0, unix.length() - ".class".length());
    }

    private static String normalizedLookupName(final String value) {
        final String normalized = value.startsWith("L")
                ? value.substring(1) : value;
        return normalized.replace('.', '/');
    }

    private static boolean isModuleInfoClass(final String value) {
        return MODULE_INFO_CLASS.equals(value.replace('\\', '/'));
    }

    private static String digest(final byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(SHA_256).digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(SHA_256 + " unavailable",
                    exception);
        }
    }
}
