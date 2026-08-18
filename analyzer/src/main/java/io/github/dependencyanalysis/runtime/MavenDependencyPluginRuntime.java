package io.github.dependencyanalysis.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Prepared Maven Dependency Plugin execution contract. */
public final class MavenDependencyPluginRuntime implements AutoCloseable {

    /** Built-in Dependency Evidence Plugin group. */
    private static final String ARTIFACT_PATH_PLUGIN_GROUP =
            "io.github.dependencyanalysis";

    /** Built-in Dependency Evidence Plugin artifact. */
    private static final String ARTIFACT_PATH_PLUGIN_ARTIFACT =
            "dependency-analyzer-artifact-path-maven-plugin";

    /** Built-in Dependency Evidence Plugin version. */
    public static final String ARTIFACT_PATH_PLUGIN_VERSION =
            BuildMetadata.getArtifactPathPluginVersion();

    /** Built-in Dependency Evidence Plugin goal. */
    public static final String DEPENDENCY_EVIDENCE_PLUGIN_GOAL =
            ARTIFACT_PATH_PLUGIN_GROUP + ":"
                    + ARTIFACT_PATH_PLUGIN_ARTIFACT + ":"
                    + ARTIFACT_PATH_PLUGIN_VERSION
                    + ":collect-dependency-evidence";

    /** Built-in Classpath Evidence Plugin goal. */
    public static final String CLASSPATH_EVIDENCE_PLUGIN_GOAL =
            ARTIFACT_PATH_PLUGIN_GROUP + ":"
                    + ARTIFACT_PATH_PLUGIN_ARTIFACT + ":"
                    + ARTIFACT_PATH_PLUGIN_VERSION
                    + ":collect-classpath-evidence";

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

    /** Prepared file repositories. */
    private final List<Path> repositories;

    /** Command-scoped paths removed when the runtime closes. */
    private final List<Path> cleanupPaths;

    /**
     * Creates a prepared plugin runtime.
     *
     * @param pluginVersion selected version
     * @param pluginGoal fully-qualified goal
     * @param arguments prepared Maven arguments
     * @param preparedRepositories embedded repositories
     * @param temporaryPaths command-scoped cleanup paths
     */
    MavenDependencyPluginRuntime(
            final String pluginVersion,
            final String pluginGoal,
            final List<String> arguments,
            final List<Path> preparedRepositories,
            final List<Path> temporaryPaths) {
        version = Objects.requireNonNull(pluginVersion, "version");
        goal = Objects.requireNonNull(pluginGoal, "goal");
        mavenArguments = List.copyOf(arguments);
        repositories = preparedRepositories.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        cleanupPaths = temporaryPaths.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
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

    /** @return built-in Dependency Evidence Plugin goal */
    public String getDependencyEvidenceGoal() {
        return DEPENDENCY_EVIDENCE_PLUGIN_GOAL;
    }

    /** @return built-in Classpath Evidence Plugin goal */
    public String getClasspathEvidenceGoal() {
        return CLASSPATH_EVIDENCE_PLUGIN_GOAL;
    }

    /** @return Maven arguments including settings overlay */
    public List<String> getMavenArguments() {
        return mavenArguments;
    }

    /** @return prepared embedded repositories in resolution order */
    public List<Path> getRepositories() {
        return repositories;
    }

    /** @return built-in Dependency Evidence Plugin version */
    public String getArtifactPathPluginVersion() {
        return ARTIFACT_PATH_PLUGIN_VERSION;
    }

    /** @return true because built-in repositories are always prepared */
    public boolean isEmbedded() {
        return true;
    }

    /** Removes command-scoped settings and Snapshot repository content. */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (int index = cleanupPaths.size() - 1;
             index >= 0; index--) {
            final Path path = cleanupPaths.get(index);
            try {
                delete(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void delete(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
                final Path[] paths = stream.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new);
                for (Path item : paths) {
                    Files.deleteIfExists(item);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }
}
