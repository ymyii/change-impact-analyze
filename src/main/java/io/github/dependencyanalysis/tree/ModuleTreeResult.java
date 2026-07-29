package io.github.dependencyanalysis.tree;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Dependency tree result for one active module. */
public final class ModuleTreeResult {

    /** Module POM. */
    private final Path pom;

    /** Module coordinate. */
    private final String coordinate;

    /** Occurrences. */
    private final List<DependencyOccurrence> occurrences;

    /** Complete verbose evidence. */
    private final boolean completeMediation;

    /** Failure reason. */
    private final String failure;

    /** Reason this module is included in bounded analysis. */
    private final ModuleAnalysisRole role;

    /**
     * Creates a module result.
     *
     * @param modulePom relative POM
     * @param moduleCoordinate coordinate
     * @param dependencies occurrences
     * @param complete complete mediation flag
     * @param failureReason failure reason
     */
    public ModuleTreeResult(
            final Path modulePom,
            final String moduleCoordinate,
            final List<DependencyOccurrence>
                    dependencies,
            final boolean complete,
            final String failureReason) {
        this(modulePom, moduleCoordinate, dependencies,
                complete, failureReason,
                ModuleAnalysisRole.REQUESTED);
    }

    /**
     * Creates a module result with its bounded-analysis role.
     *
     * @param modulePom relative POM
     * @param moduleCoordinate coordinate
     * @param dependencies occurrences
     * @param complete complete mediation flag
     * @param failureReason failure reason
     * @param analysisRole inclusion role
     */
    public ModuleTreeResult(
            final Path modulePom,
            final String moduleCoordinate,
            final List<DependencyOccurrence>
                    dependencies,
            final boolean complete,
            final String failureReason,
            final ModuleAnalysisRole analysisRole) {
        pom = modulePom;
        coordinate = moduleCoordinate;
        occurrences = List.copyOf(dependencies);
        completeMediation = complete;
        failure = failureReason;
        role = Objects.requireNonNull(
                analysisRole, "analysisRole");
    }

    /** @return module POM */
    public Path getPom() {
        return pom;
    }

    /** @return module coordinate */
    public String getCoordinate() {
        return coordinate;
    }

    /** @return dependency occurrences */
    public List<DependencyOccurrence>
            getOccurrences() {
        return occurrences;
    }

    /** @return complete verbose evidence */
    public boolean isCompleteMediation() {
        return completeMediation;
    }

    /** @return failure reason */
    public String getFailure() {
        return failure;
    }

    /** @return bounded-analysis inclusion role */
    public ModuleAnalysisRole getRole() {
        return role;
    }
}
