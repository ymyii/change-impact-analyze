package io.github.dependencyanalysis.reactor;

import java.nio.file.Path;
import java.util.List;

/** Minimal safe POM model for reactor discovery. */
public final class PomDescriptor {

    /** Repository-relative POM path. */
    private final Path path;

    /** Project coordinate. */
    private final String coordinate;

    /** Maven packaging. */
    private final String packaging;

    /** All declared module paths. */
    private final List<String> declaredModules;

    /** Active module paths. */
    private final List<String> activeModules;

    /** Isolated parse failure. */
    private final String failure;

    /**
     * Creates a descriptor.
     *
     * @param pomPath relative POM path
     * @param projectCoordinate coordinate
     * @param projectPackaging Maven packaging
     * @param allModules all module declarations
     * @param enabledModules active modules
     * @param parseFailure isolated parse failure
     */
    public PomDescriptor(
            final Path pomPath,
            final String projectCoordinate,
            final String projectPackaging,
            final List<String> allModules,
            final List<String> enabledModules,
            final String parseFailure) {
        path = pomPath;
        coordinate = projectCoordinate;
        packaging = projectPackaging;
        declaredModules = List.copyOf(allModules);
        activeModules = List.copyOf(enabledModules);
        failure = parseFailure;
    }

    /** @return repository-relative POM path */
    public Path getPath() {
        return path;
    }

    /** @return project coordinate */
    public String getCoordinate() {
        return coordinate;
    }

    /** @return Maven packaging */
    public String getPackaging() {
        return packaging;
    }

    /** @return all module declarations */
    public List<String> getDeclaredModules() {
        return declaredModules;
    }

    /** @return active module declarations */
    public List<String> getActiveModules() {
        return activeModules;
    }

    /** @return isolated parse failure */
    public String getFailure() {
        return failure;
    }
}
