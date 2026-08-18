package io.github.dependencyanalysis.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable Maven runtime selected for a command. */
public final class MavenRuntimeDescriptor {

    /** Runtime origin. */
    private final MavenRuntimeSource source;

    /** Absolute executable path. */
    private final Path executable;

    /** Detected version, if already probed. */
    private final MavenVersion version;

    /** Maven subprocess JAVA_HOME override. */
    private final Path javaHome;

    /** Complete application config directory. */
    private final Path configDir;

    /** Runtime default global settings, when discoverable. */
    private final Path defaultGlobalSettings;

    /**
     * Creates a runtime descriptor.
     *
     * @param runtimeSource source
     * @param runtimeExecutable executable
     * @param runtimeVersion version, nullable
     * @param runtimeJavaHome Java home, nullable
     * @param runtimeConfigDir config directory
     */
    public MavenRuntimeDescriptor(
            final MavenRuntimeSource runtimeSource,
            final Path runtimeExecutable,
            final MavenVersion runtimeVersion,
            final Path runtimeJavaHome,
            final Path runtimeConfigDir) {
        this(runtimeSource, runtimeExecutable, runtimeVersion,
                runtimeJavaHome, runtimeConfigDir,
                inferGlobalSettings(runtimeExecutable));
    }

    private MavenRuntimeDescriptor(
            final MavenRuntimeSource runtimeSource,
            final Path runtimeExecutable,
            final MavenVersion runtimeVersion,
            final Path runtimeJavaHome,
            final Path runtimeConfigDir,
            final Path runtimeGlobalSettings) {
        source = Objects.requireNonNull(runtimeSource, "source");
        executable = Objects.requireNonNull(
                runtimeExecutable, "executable")
                .toAbsolutePath().normalize();
        version = runtimeVersion;
        javaHome = runtimeJavaHome == null
                ? null : runtimeJavaHome.toAbsolutePath().normalize();
        configDir = Objects.requireNonNull(runtimeConfigDir, "configDir")
                .toAbsolutePath().normalize();
        defaultGlobalSettings = runtimeGlobalSettings;
    }

    /** @return runtime source */
    public MavenRuntimeSource getSource() {
        return source;
    }

    /** @return executable path */
    public Path getExecutable() {
        return executable;
    }

    /** @return detected version, or null */
    public MavenVersion getVersion() {
        return version;
    }

    /** @return JAVA_HOME override, or null */
    public Path getJavaHome() {
        return javaHome;
    }

    /** @return config directory */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * Returns the configured runtime's default global settings when present.
     *
     * @return {@code conf/settings.xml}, or null when it cannot be resolved
     */
    public Path getDefaultGlobalSettings() {
        return defaultGlobalSettings;
    }

    /**
     * Returns a copy with detected version.
     *
     * @param detected detected version
     * @return new descriptor
     */
    public MavenRuntimeDescriptor withVersion(final MavenVersion detected) {
        return new MavenRuntimeDescriptor(
                source, executable, detected, javaHome, configDir,
                defaultGlobalSettings);
    }

    /**
     * Returns a copy enriched from the actual Maven version probe.
     *
     * @param detected detected version
     * @param output complete {@code mvn --version} output
     * @return enriched runtime descriptor
     */
    public MavenRuntimeDescriptor withProbe(
            final MavenVersion detected,
            final String output) {
        Path settings = defaultGlobalSettings;
        for (String line : output.lines().toList()) {
            final String value = line.trim();
            if (value.startsWith("Maven home:")) {
                final Path home = Path.of(value.substring(
                        "Maven home:".length()).trim());
                final Path candidate = home.resolve("conf/settings.xml");
                if (Files.isRegularFile(candidate)) {
                    settings = candidate.toAbsolutePath().normalize();
                }
            }
        }
        return new MavenRuntimeDescriptor(
                source, executable, detected, javaHome, configDir, settings);
    }

    private static Path inferGlobalSettings(final Path runtimeExecutable) {
        try {
            final Path real = runtimeExecutable.toRealPath();
            final Path bin = real.getParent();
            final Path home = bin == null ? null : bin.getParent();
            final Path settings = home == null ? null
                    : home.resolve("conf/settings.xml");
            return settings != null && Files.isRegularFile(settings)
                    ? settings : null;
        } catch (IOException exception) {
            return null;
        }
    }
}
