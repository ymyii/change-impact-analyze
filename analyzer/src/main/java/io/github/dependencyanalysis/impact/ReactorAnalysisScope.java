package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Requested analysis scope resolved against one Maven reactor. */
final class ReactorAnalysisScope {

    /** Selected mode. */
    private final AnalysisMode mode;

    /** Git checkout root. */
    private final Path repositoryRoot;

    /** Maven reactor root directory. */
    private final Path reactorRoot;

    /** Modules to report and analyze. */
    private final List<ModuleId> modules;

    /** All active analysis-eligible reactor modules. */
    private final List<ModuleId> allModules;

    /** All active reactor coordinates. */
    private final Set<ArtifactCoord> reactorCoordinates;

    /** Maven project selector tokens. */
    private final List<String> projectArguments;

    /**
     * Creates a resolved reactor scope.
     *
     * @param analysisMode selected mode
     * @param repository checkout root
     * @param root reactor root
     * @param selectedModules modules selected for analysis
     * @param activeModules all active analysis-eligible modules
     * @param coordinates all active reactor coordinates
     * @param arguments Maven project selector arguments
     */
    ReactorAnalysisScope(
            final AnalysisMode analysisMode,
            final Path repository,
            final Path root,
            final List<ModuleId> selectedModules,
            final List<ModuleId> activeModules,
            final Set<ArtifactCoord> coordinates,
            final List<String> arguments) {
        mode = analysisMode;
        repositoryRoot = repository;
        reactorRoot = root;
        modules = Collections.unmodifiableList(
                new ArrayList<>(selectedModules));
        allModules = Collections.unmodifiableList(
                new ArrayList<>(activeModules));
        reactorCoordinates = Collections.unmodifiableSet(
                new LinkedHashSet<>(coordinates));
        projectArguments = List.copyOf(arguments);
    }

    /** @return selected analysis mode */
    AnalysisMode getMode() {
        return mode;
    }

    /** @return checkout root */
    Path getRepositoryRoot() {
        return repositoryRoot;
    }

    /** @return reactor root */
    Path getReactorRoot() {
        return reactorRoot;
    }

    /** @return selected analysis modules */
    List<ModuleId> getModules() {
        return modules;
    }

    /** @return all active analysis-eligible reactor modules */
    List<ModuleId> getAllModules() {
        return allModules;
    }

    /** @return all active reactor coordinates */
    Set<ArtifactCoord> getReactorCoordinates() {
        return reactorCoordinates;
    }

    /** @return Maven project selector tokens */
    List<String> getProjectArguments() {
        return projectArguments;
    }
}
