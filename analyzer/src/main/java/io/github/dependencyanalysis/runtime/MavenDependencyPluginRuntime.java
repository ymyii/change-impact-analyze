package io.github.dependencyanalysis.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Prepared Maven Dependency Plugin execution contract. */
public final class MavenDependencyPluginRuntime {

    /** Built-in Artifact Path Plugin group. */
    private static final String ARTIFACT_PATH_PLUGIN_GROUP =
            "io.github.dependencyanalysis";

    /** Built-in Artifact Path Plugin artifact. */
    private static final String ARTIFACT_PATH_PLUGIN_ARTIFACT =
            "dependency-analyzer-artifact-path-maven-plugin";

    /** Built-in Artifact Path Plugin version. */
    public static final String ARTIFACT_PATH_PLUGIN_VERSION =
            BuildMetadata.getArtifactPathPluginVersion();

    /** Built-in Artifact Path Plugin goal. */
    public static final String ARTIFACT_PATH_PLUGIN_GOAL =
            ARTIFACT_PATH_PLUGIN_GROUP + ":"
                    + ARTIFACT_PATH_PLUGIN_ARTIFACT + ":"
                    + ARTIFACT_PATH_PLUGIN_VERSION
                    + ":resolve-artifact-paths";

    /** Maven Dependency Plugin group. */
    private static final String PLUGIN_GROUP =
            "org.apache.maven.plugins";

    /** Maven Dependency Plugin artifact. */
    private static final String PLUGIN_ARTIFACT =
            "maven-dependency-plugin";

    /** Selected plugin version. */
    private final String version;

    /** Fully-qualified Maven goal. */
    private final String goal;

    /** Maven arguments with the settings overlay applied. */
    private final List<String> mavenArguments;

    /** Embedded repository, or null for an override. */
    private final Path repository;

    /** Embedded repository SHA-512, empty for an override. */
    private final String repositorySha512;

    /** Embedded Artifact Path Plugin JAR SHA-512. */
    private final String artifactPathJarSha512;

    /**
     * Creates a prepared plugin runtime.
     *
     * @param pluginVersion selected version
     * @param pluginGoal fully-qualified goal
     * @param arguments prepared Maven arguments
     * @param embeddedRepository repository, nullable
     * @param sha512 embedded runtime fingerprint
     * @param artifactPathSha512 embedded Artifact Path Plugin JAR checksum
     */
    MavenDependencyPluginRuntime(
            final String pluginVersion,
            final String pluginGoal,
            final List<String> arguments,
            final Path embeddedRepository,
            final String sha512,
            final String artifactPathSha512) {
        version = Objects.requireNonNull(
                pluginVersion, "version");
        goal = Objects.requireNonNull(
                pluginGoal, "goal");
        mavenArguments = List.copyOf(arguments);
        repository = embeddedRepository == null
                ? null : embeddedRepository
                .toAbsolutePath().normalize();
        repositorySha512 = Objects.requireNonNull(
                sha512, "repositorySha512");
        artifactPathJarSha512 = Objects.requireNonNull(
                artifactPathSha512, "artifactPathJarSha512");
    }

    /** @return selected plugin version */
    public String getVersion() {
        return version;
    }

    /** @return fully-qualified goal */
    public String getGoal() {
        return goal;
    }

    /**
     * Returns a fully-qualified goal for the selected Plugin version.
     *
     * @param goalName Maven goal name
     * @return fully-qualified Maven goal
     */
    public String getGoal(final String goalName) {
        if (goalName == null
                || !goalName.matches("[A-Za-z][A-Za-z0-9-]*")) {
            throw new IllegalArgumentException(
                    "Invalid Maven Dependency Plugin goal: " + goalName);
        }
        return PLUGIN_GROUP + ":" + PLUGIN_ARTIFACT
                + ":" + version + ":" + goalName;
    }

    /**
     * Returns the built-in Artifact Path Plugin goal.
     *
     * @return fully-qualified artifact path goal
     */
    public String getArtifactPathGoal() {
        return ARTIFACT_PATH_PLUGIN_GOAL;
    }

    /** @return Maven arguments including settings overlay */
    public List<String> getMavenArguments() {
        return mavenArguments;
    }

    /** @return embedded repository, or null for override */
    public Path getRepository() {
        return repository;
    }

    /** @return embedded repository archive SHA-512 */
    public String getRepositorySha512() {
        return repositorySha512;
    }

    /** @return built-in Artifact Path Plugin version */
    public String getArtifactPathPluginVersion() {
        return ARTIFACT_PATH_PLUGIN_VERSION;
    }

    /** @return embedded Artifact Path Plugin JAR SHA-512 */
    public String getArtifactPathJarSha512() {
        return artifactPathJarSha512;
    }

    /** @return true when the bundled repository is selected */
    public boolean isEmbedded() {
        return repository != null;
    }
}
