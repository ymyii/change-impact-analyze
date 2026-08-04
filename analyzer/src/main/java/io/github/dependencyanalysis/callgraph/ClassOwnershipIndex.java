package io.github.dependencyanalysis.callgraph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Binary-name ownership index with deterministic duplicate validation. */
public final class ClassOwnershipIndex {

    /** Java Module Descriptor class entry. */
    private static final String MODULE_INFO_CLASS = "module-info.class";

    /** SHA-256 algorithm name. */
    private static final String SHA_256 = "SHA-256";

    /** Lowest ownership priority. */
    private static final int FALLBACK_PRIORITY = 4;

    /** External dependency ownership priority. */
    private static final int DEPENDENCY_PRIORITY = 3;

    /** Indexed classes. */
    private final Map<String, ClassOwnership> classes = new HashMap<>();

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
            add(name, origin, directory, digest(Files.readAllBytes(file)));
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
                            jarPath, digest(input.readAllBytes()));
                }
            }
        }
    }

    /**
     * Validates an additional JAR against already indexed classes without
     * retaining its non-conflicting entries. This keeps JDK duplicate checks
     * cheap while still preventing loader-order first-wins behavior.
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
                    add(name, origin, jarPath,
                            digest(input.readAllBytes()));
                }
            }
        }
    }

    private void add(
            final String name,
            final CodeOrigin origin,
            final Path source,
            final String digest) {
        final ClassOwnership previous = classes.get(name);
        if (previous != null && !previous.getDigest().equals(digest)) {
            throw new CallGraphException(
                    "Conflicting duplicate class " + name + ": "
                            + previous.getSource() + " and " + source);
        }
        if (previous == null || priority(origin)
                < priority(previous.getOrigin())) {
            classes.put(name, new ClassOwnership(origin, source, digest));
        }
    }

    /**
     * Returns ownership for a WALA-style or internal binary name.
     *
     * @param name class name
     * @return ownership, or null for JDK/unindexed classes
     */
    public ClassOwnership ownershipOf(final String name) {
        final String normalized = name.startsWith("L")
                ? name.substring(1) : name;
        return classes.get(normalized.replace('.', '/'));
    }

    /** @return indexed binary names */
    public List<String> binaryNames() {
        final List<String> result = new ArrayList<>(classes.keySet());
        result.sort(String::compareTo);
        return List.copyOf(result);
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
