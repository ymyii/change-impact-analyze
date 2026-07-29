package io.github.dependencyanalysis.tree;

import java.nio.file.Path;
import java.util.List;

/** Minimal safe POM model for reactor discovery. */
final class PomDescriptor {

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
    PomDescriptor(
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
    Path getPath() {
        return path;
    }

    /** @return project coordinate */
    String getCoordinate() {
        return coordinate;
    }

    /** @return Maven packaging */
    String getPackaging() {
        return packaging;
    }

    /** @return all module declarations */
    List<String> getDeclaredModules() {
        return declaredModules;
    }

    /** @return active module declarations */
    List<String> getActiveModules() {
        return activeModules;
    }

    /** @return isolated parse failure */
    String getFailure() {
        return failure;
    }
}
