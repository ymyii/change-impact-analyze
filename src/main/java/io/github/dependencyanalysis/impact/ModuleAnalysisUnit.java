package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ResolvedArtifact;

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
    private final List<ResolvedArtifact> targetArtifacts;

    /** Baseline resolved external dependencies for old-side SSA. */
    private final List<ResolvedArtifact> baselineArtifacts;

    /** Module-bound ChangePoints. */
    private final List<BoundChangePoint> changePoints;

    /** Isolated physical JAR diff failures for this module. */
    private final List<String> jarDiffFailures;

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
            final List<ResolvedArtifact> targetDependencies,
            final List<ResolvedArtifact> baselineDependencies,
            final ModuleChangeSet changes) {
        moduleId = Objects.requireNonNull(id, "moduleId");
        presence = Objects.requireNonNull(modulePresence, "presence");
        projectClasses = Objects.requireNonNull(classes,
                "projectClasses");
        reactorDependencyClasses = immutable(reactorClasses);
        targetArtifacts = immutable(targetDependencies);
        baselineArtifacts = immutable(baselineDependencies);
        changePoints = immutable(changes.changePoints());
        jarDiffFailures = immutable(changes.jarDiffFailures());
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
    public List<ResolvedArtifact> getTargetArtifacts() {
        return targetArtifacts;
    }

    /** @return baseline resolved artifacts */
    public List<ResolvedArtifact> getBaselineArtifacts() {
        return baselineArtifacts;
    }

    /** @return module-bound ChangePoints */
    public List<BoundChangePoint> getChangePoints() {
        return changePoints;
    }

    /** @return isolated physical JAR diff failure evidence */
    public List<String> getJarDiffFailures() {
        return jarDiffFailures;
    }
}
