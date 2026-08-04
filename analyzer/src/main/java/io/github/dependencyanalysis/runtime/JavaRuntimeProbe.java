package io.github.dependencyanalysis.runtime;

import io.github.dependencyanalysis.util.CommandResolver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// Wiki: wiki/features/cli-preflight-diagnostics.md - impact JDK 8 probe
/** Resolves and validates the JDK used by the analyzed project. */
public final class JavaRuntimeProbe {

    /** Supported impact target Java major. */
    private static final int TARGET_JAVA_MAJOR = 8;

    /** Prefix length for legacy Java versions. */
    private static final int LEGACY_VERSION_PREFIX = 2;

    /** Length of the property assignment delimiter. */
    private static final int PROPERTY_DELIMITER_LENGTH = 3;

    /** Java version property. */
    private static final String JAVA_VERSION =
            "java.version";

    /** Runtime Java home property. */
    private static final String JAVA_HOME =
            "java.home";

    /** JDK 8 boot class path property. */
    private static final String BOOT_PATH =
            "sun.boot.class.path";

    /** JDK extension directories property. */
    private static final String EXT_DIRS =
            "java.ext.dirs";

    /**
     * Probes a JDK 8 home.
     *
     * @param configuredHome configured JDK home
     * @return validated descriptor
     */
    public JavaRuntimeDescriptor probeJdk8(
            final Path configuredHome) {
        if (configuredHome == null) {
            throw new JavaRuntimeException(
                    "impact requires --java-home"
                            + " pointing to a JDK 8 home");
        }
        final Path home = configuredHome
                .toAbsolutePath().normalize();
        final Path java = executable(home, "java");
        final Path javac = executable(home, "javac");
        requireExecutable(java, "java");
        requireExecutable(javac, "javac");
        final Map<String, String> properties =
                readProperties(java);
        final String version = properties.get(
                JAVA_VERSION);
        final int major = parseMajor(version);
        if (major != TARGET_JAVA_MAJOR) {
            throw new JavaRuntimeException(
                    "impact currently supports target"
                            + " JDK 8 only; detected "
                            + version + " at " + home);
        }
        final Path runtimeHome = Path.of(
                required(properties, JAVA_HOME));
        final List<Path> boot = bootClassPath(
                properties, runtimeHome);
        if (boot.stream().noneMatch(path ->
                "rt.jar".equals(path.getFileName()
                        .toString()))) {
            throw new JavaRuntimeException(
                    "JDK 8 rt.jar is unavailable under "
                            + home);
        }
        final List<Path> extensions =
                extensionClassPath(properties);
        return new JavaRuntimeDescriptor(
                home, runtimeHome, version,
                major, boot, extensions);
    }

    private Path executable(
            final Path home,
            final String name) {
        final String suffix = System.getProperty(
                "os.name", "").toLowerCase()
                .contains("win") ? ".exe" : "";
        return home.resolve("bin")
                .resolve(name + suffix);
    }

    private void requireExecutable(
            final Path executable,
            final String name) {
        if (!Files.isRegularFile(executable)
                || (!isWindows()
                && !Files.isExecutable(executable))) {
            throw new JavaRuntimeException(
                    "JDK " + name
                            + " is unavailable: "
                            + executable);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase().contains("win");
    }

    private Map<String, String> readProperties(
            final Path javaExecutable) {
        final List<String> command =
                CommandResolver.resolve(List.of(
                        javaExecutable.toString(),
                        "-XshowSettings:properties",
                        "-version"));
        try {
            final Process process = new ProcessBuilder(
                    command)
                    .redirectErrorStream(true)
                    .start();
            final String output = new String(
                    process.getInputStream()
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new JavaRuntimeException(
                        "JDK probe failed ("
                                + exitCode + "): "
                                + output.trim());
            }
            return parseProperties(output);
        } catch (IOException exception) {
            throw new JavaRuntimeException(
                    "Unable to execute target JDK",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JavaRuntimeException(
                    "Target JDK probe interrupted",
                    exception);
        }
    }

    static Map<String, String> parseProperties(
            final String output) {
        final Map<String, String> result =
                new LinkedHashMap<>();
        String currentKey = null;
        for (String line : output.split("\\R")) {
            final String trimmed = line.trim();
            final int equals = trimmed.indexOf(" = ");
            if (equals > 0) {
                currentKey = trimmed.substring(
                        0, equals).trim();
                result.put(currentKey,
                        trimmed.substring(equals
                                + PROPERTY_DELIMITER_LENGTH)
                                .trim());
            } else if (currentKey != null
                    && line.startsWith("        ")
                    && !trimmed.isEmpty()) {
                result.compute(currentKey,
                        (key, value) -> value.isEmpty()
                                ? trimmed
                                : value + File.pathSeparator
                                + trimmed);
            }
        }
        return result;
    }

    static int parseMajor(
            final String version) {
        if (version == null || version.isBlank()) {
            throw new JavaRuntimeException(
                    "Target JDK version is unavailable");
        }
        final String value = version.startsWith("1.")
                ? version.substring(LEGACY_VERSION_PREFIX) : version;
        final String digits = value.split(
                "[._+-]", 2)[0];
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            throw new JavaRuntimeException(
                    "Unsupported target JDK version: "
                            + version, exception);
        }
    }

    private String required(
            final Map<String, String> properties,
            final String name) {
        final String value = properties.get(name);
        if (value == null || value.isBlank()) {
            throw new JavaRuntimeException(
                    "Target JDK property is unavailable: "
                            + name);
        }
        return value;
    }

    private List<Path> bootClassPath(
            final Map<String, String> properties,
            final Path runtimeHome) {
        final String configured = properties.get(
                BOOT_PATH);
        final List<Path> entries = configured == null
                ? new ArrayList<>()
                : existingJars(configured);
        if (entries.isEmpty()) {
            addIfJar(entries,
                    runtimeHome.resolve("lib")
                            .resolve("rt.jar"));
        }
        return List.copyOf(entries);
    }

    private List<Path> extensionClassPath(
            final Map<String, String> properties) {
        final String value = properties.get(EXT_DIRS);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        final Set<Path> entries =
                new LinkedHashSet<>();
        for (String directory : value.split(
                java.util.regex.Pattern.quote(
                        File.pathSeparator))) {
            final Path path = Path.of(directory);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> files = Files.list(path)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isJar)
                        .sorted(Comparator.comparing(
                                Path::toString))
                        .map(item -> item.toAbsolutePath()
                                .normalize())
                        .forEach(entries::add);
            } catch (IOException exception) {
                throw new JavaRuntimeException(
                        "Unable to inspect JDK extension"
                                + " directory: " + path,
                        exception);
            }
        }
        return List.copyOf(entries);
    }

    private List<Path> existingJars(
            final String classPath) {
        final List<Path> result =
                new ArrayList<>();
        for (String value : classPath.split(
                java.util.regex.Pattern.quote(
                        File.pathSeparator))) {
            if (!value.isBlank()) {
                addIfJar(result, Path.of(value));
            }
        }
        return result;
    }

    private void addIfJar(
            final List<Path> result,
            final Path path) {
        if (Files.isRegularFile(path) && isJar(path)) {
            result.add(path.toAbsolutePath()
                    .normalize());
        }
    }

    private boolean isJar(final Path path) {
        return path.getFileName().toString()
                .toLowerCase().endsWith(".jar");
    }
}
