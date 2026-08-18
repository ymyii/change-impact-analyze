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

    /** Class conflicts on this Module's effective classpath. */
    private final List<TreeClassConflict> classConflicts;

    /** Classpath collection or scanning completeness issues. */
    private final List<String> classpathIssues;

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
                ModuleAnalysisRole.REQUESTED, ClassAnalysisData.EMPTY);
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
        this(modulePom, moduleCoordinate, dependencies, complete,
                failureReason, analysisRole, ClassAnalysisData.EMPTY);
    }

    /**
     * Creates a complete dependency and classpath result.
     *
     * @param modulePom relative POM
     * @param moduleCoordinate coordinate
     * @param dependencies occurrences
     * @param complete complete mediation flag
     * @param failureReason dependency collection failure
     * @param analysisRole inclusion role
     * @param analysis aggregated classpath analysis
     */
    private ModuleTreeResult(
            final Path modulePom,
            final String moduleCoordinate,
            final List<DependencyOccurrence> dependencies,
            final boolean complete,
            final String failureReason,
            final ModuleAnalysisRole analysisRole,
            final ClassAnalysisData analysis) {
        pom = modulePom;
        coordinate = moduleCoordinate;
        occurrences = List.copyOf(dependencies);
        completeMediation = complete;
        failure = failureReason;
        role = Objects.requireNonNull(analysisRole, "analysisRole");
        classConflicts = analysis.conflicts();
        classpathIssues = analysis.issues();
    }

    /**
     * Returns a copy carrying classpath conflict analysis.
     *
     * @param conflicts class conflicts
     * @param evidenceIssues incomplete classpath reasons
     * @return enriched immutable result
     */
    public ModuleTreeResult withClassAnalysis(
            final List<TreeClassConflict> conflicts,
            final List<String> evidenceIssues) {
        return new ModuleTreeResult(pom, coordinate, occurrences,
                completeMediation, failure, role,
                new ClassAnalysisData(conflicts, evidenceIssues));
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

    /** @return stable conflict rows */
    public List<TreeClassConflict> getClassConflicts() {
        return classConflicts;
    }

    /** @return classpath completeness issues */
    public List<String> getClasspathIssues() {
        return classpathIssues;
    }

    /**
     * Aggregates optional class analysis without widening constructors.
     *
     * @param conflicts class conflicts
     * @param issues completeness issues
     */
    private record ClassAnalysisData(
            List<TreeClassConflict> conflicts,
            List<String> issues) {

        /** Empty analysis for compatibility constructors. */
        private static final ClassAnalysisData EMPTY =
                new ClassAnalysisData(List.of(), List.of());

        ClassAnalysisData {
            conflicts = List.copyOf(conflicts);
            issues = List.copyOf(issues);
        }
    }
}
