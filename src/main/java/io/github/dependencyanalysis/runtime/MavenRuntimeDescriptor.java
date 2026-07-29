package io.github.dependencyanalysis.runtime;

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

    /** Embedded ZIP SHA-512, empty for user runtime. */
    private final String distributionSha512;

    /**
     * Creates a runtime descriptor.
     *
     * @param runtimeSource source
     * @param runtimeExecutable executable
     * @param runtimeVersion version, nullable
     * @param runtimeJavaHome Java home, nullable
     * @param runtimeConfigDir config directory
     * @param sha512 distribution checksum
     */
    public MavenRuntimeDescriptor(
            final MavenRuntimeSource runtimeSource,
            final Path runtimeExecutable,
            final MavenVersion runtimeVersion,
            final Path runtimeJavaHome,
            final Path runtimeConfigDir,
            final String sha512) {
        source = Objects.requireNonNull(
                runtimeSource, "source");
        executable = Objects.requireNonNull(
                runtimeExecutable, "executable")
                .toAbsolutePath().normalize();
        version = runtimeVersion;
        javaHome = runtimeJavaHome == null
                ? null : runtimeJavaHome
                .toAbsolutePath().normalize();
        configDir = Objects.requireNonNull(
                runtimeConfigDir, "configDir")
                .toAbsolutePath().normalize();
        distributionSha512 = Objects.requireNonNull(
                sha512, "distributionSha512");
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

    /** @return embedded distribution checksum */
    public String getDistributionSha512() {
        return distributionSha512;
    }

    /**
     * Returns a copy with detected version.
     *
     * @param detected detected version
     * @return new descriptor
     */
    public MavenRuntimeDescriptor withVersion(
            final MavenVersion detected) {
        return new MavenRuntimeDescriptor(
                source, executable, detected,
                javaHome, configDir,
                distributionSha512);
    }
}
