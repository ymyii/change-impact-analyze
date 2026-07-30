package io.github.dependencyanalysis.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable target Java runtime used by impact analysis. */
public final class JavaRuntimeDescriptor {

    /** Configured JDK home. */
    private final Path javaHome;

    /** Runtime-reported Java home. */
    private final Path runtimeHome;

    /** Full Java version. */
    private final String version;

    /** Java major version. */
    private final int majorVersion;

    /** JDK boot class path entries. */
    private final List<Path> bootClassPath;

    /** JDK extension class path entries. */
    private final List<Path> extensionClassPath;

    /**
     * Creates a descriptor.
     *
     * @param configuredHome configured JDK home
     * @param reportedHome runtime-reported home
     * @param javaVersion full Java version
     * @param major Java major version
     * @param bootEntries boot class path
     * @param extensionEntries extension class path
     */
    public JavaRuntimeDescriptor(
            final Path configuredHome,
            final Path reportedHome,
            final String javaVersion,
            final int major,
            final List<Path> bootEntries,
            final List<Path> extensionEntries) {
        javaHome = normalize(configuredHome,
                "configuredHome");
        runtimeHome = normalize(reportedHome,
                "reportedHome");
        version = Objects.requireNonNull(
                javaVersion, "javaVersion");
        majorVersion = major;
        bootClassPath = normalizedCopy(
                bootEntries, "bootEntries");
        extensionClassPath = normalizedCopy(
                extensionEntries, "extensionEntries");
    }

    private static Path normalize(
            final Path value,
            final String name) {
        return Objects.requireNonNull(value, name)
                .toAbsolutePath().normalize();
    }

    private static List<Path> normalizedCopy(
            final List<Path> values,
            final String name) {
        return Objects.requireNonNull(values, name)
                .stream()
                .map(path -> normalize(path, "path"))
                .toList();
    }

    /** @return configured JDK home */
    public Path getJavaHome() {
        return javaHome;
    }

    /** @return runtime-reported Java home */
    public Path getRuntimeHome() {
        return runtimeHome;
    }

    /** @return full Java version */
    public String getVersion() {
        return version;
    }

    /** @return Java major version */
    public int getMajorVersion() {
        return majorVersion;
    }

    /** @return boot class path entries */
    public List<Path> getBootClassPath() {
        return bootClassPath;
    }

    /** @return extension class path entries */
    public List<Path> getExtensionClassPath() {
        return extensionClassPath;
    }
}
