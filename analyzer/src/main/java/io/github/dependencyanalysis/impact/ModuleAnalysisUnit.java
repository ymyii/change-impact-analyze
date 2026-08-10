package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Complete immutable input for one module Call Graph analysis. */
public final class ModuleAnalysisUnit {

    /** Module identity. */
    private final ModuleId moduleId;

    /** Cross-side module presence. */
    private final ModulePresence presence;

    /** Current module classes directory. */
    private final Path projectClasses;

    /** Reactor dependency classes directories. */
    private final List<Path> reactorDependencyClasses;

    /** Target resolved external dependencies. */
    private final List<ArtifactCoord> targetArtifacts;

    /** Baseline resolved external dependencies for old-side SSA. */
    private final List<ArtifactCoord> baselineArtifacts;

    /** Complete module dependency changes. */
    private final List<DependencyChange> dependencyChanges;

    /** Module-bound ChangePoints. */
    private final List<BoundChangePoint> changePoints;

    /** Isolated coordinate-pair JAR diff failures for this module. */
    private final List<JarDiffFailure> jarDiffFailures;

    /** External dependency path selection and method-body policy. */
    private final ModuleChangedPathSelection changedPathSelection;

    /**
     * Creates a module analysis unit.
     *
     * @param id module identity
     * @param modulePresence cross-side presence
     * @param classes current module classes
     * @param reactorClasses reactor dependency classes
     * @param targetDependencies target external dependencies
     * @param baselineDependencies baseline external dependencies
     * @param changes bound ChangePoints and isolated diff failures
     */
    public ModuleAnalysisUnit(
            final ModuleId id,
            final ModulePresence modulePresence,
            final Path classes,
            final List<Path> reactorClasses,
            final List<ArtifactCoord> targetDependencies,
            final List<ArtifactCoord> baselineDependencies,
            final ModuleChangeSet changes) {
        this(id, modulePresence, classes, reactorClasses,
                new ModuleDependencyInputs(targetDependencies,
                        baselineDependencies,
                        ModuleChangedPathSelection.fullArtifacts(
                                targetDependencies)), changes);
    }

    /**
     * Creates a module analysis unit with planned dependency body scope.
     *
     * @param id module identity
     * @param modulePresence cross-side presence
     * @param classes current module classes
     * @param reactorClasses reactor dependency classes
     * @param dependencyInputs external dependency inputs and path selection
     * @param changes bound changes
     */
    public ModuleAnalysisUnit(
            final ModuleId id,
            final ModulePresence modulePresence,
            final Path classes,
            final List<Path> reactorClasses,
            final ModuleDependencyInputs dependencyInputs,
            final ModuleChangeSet changes) {
        moduleId = Objects.requireNonNull(id, "moduleId");
        presence = Objects.requireNonNull(modulePresence, "presence");
        projectClasses = Objects.requireNonNull(classes,
                "projectClasses");
        reactorDependencyClasses = immutable(reactorClasses);
        targetArtifacts = immutable(dependencyInputs.targetArtifacts());
        baselineArtifacts = immutable(dependencyInputs.baselineArtifacts());
        dependencyChanges = immutable(changes.dependencyChanges());
        changePoints = immutable(changes.changePoints());
        jarDiffFailures = immutable(changes.jarDiffFailures());
        changedPathSelection = Objects.requireNonNull(
                dependencyInputs.changedPathSelection(), "pathSelection");
    }

    private static <T> List<T> immutable(final List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(values, "values")));
    }

    /** @return module identity */
    public ModuleId getModuleId() {
        return moduleId;
    }

    /** @return cross-side module presence */
    public ModulePresence getPresence() {
        return presence;
    }

    /** @return current module classes directory */
    public Path getProjectClasses() {
        return projectClasses;
    }

    /** @return reactor dependency classes directories */
    public List<Path> getReactorDependencyClasses() {
        return reactorDependencyClasses;
    }

    /** @return target resolved artifacts */
    public List<ArtifactCoord> getTargetArtifacts() {
        return targetArtifacts;
    }

    /** @return baseline resolved artifacts */
    public List<ArtifactCoord> getBaselineArtifacts() {
        return baselineArtifacts;
    }

    /** @return complete module dependency changes */
    public List<DependencyChange> getDependencyChanges() {
        return dependencyChanges;
    }

    /** @return module-bound ChangePoints */
    public List<BoundChangePoint> getChangePoints() {
        return changePoints;
    }

    /** @return isolated coordinate-pair JAR diff failure evidence */
    public List<JarDiffFailure> getJarDiffFailures() {
        return jarDiffFailures;
    }

    /** @return concise JAR comparison failure diagnostics */
    public List<String> getJarDiffFailureSummaries() {
        return jarDiffFailures.stream()
                .map(JarDiffFailure::summary).toList();
    }

    /** @return dependency path selection and body policy */
    public ModuleChangedPathSelection getChangedPathSelection() {
        return changedPathSelection;
    }
}
