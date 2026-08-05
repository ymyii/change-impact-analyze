package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.DependencyChange;

import java.util.List;

/**
 * Module-bound bytecode changes and isolated coordinate-pair JAR diff failures.
 *
 * @param dependencyChanges complete module dependency changes
 * @param changePoints module-bound ChangePoints
 * @param jarDiffFailures coordinate-pair JAR diff failure evidence
 */
public record ModuleChangeSet(
        List<DependencyChange> dependencyChanges,
        List<BoundChangePoint> changePoints,
        List<JarDiffFailure> jarDiffFailures) {

    /**
     * Creates an immutable deterministic module change set.
     *
     * @param dependencyChanges complete module dependency changes
     * @param changePoints module-bound ChangePoints
     * @param jarDiffFailures coordinate-pair JAR diff failure evidence
     */
    public ModuleChangeSet {
        dependencyChanges = List.copyOf(dependencyChanges);
        changePoints = List.copyOf(changePoints);
        jarDiffFailures = List.copyOf(jarDiffFailures);
    }

    /**
     * Compatibility constructor for callers without dependency changes.
     *
     * @param changePoints module-bound ChangePoints
     * @param jarDiffFailures coordinate-pair JAR diff failures
     */
    public ModuleChangeSet(
            final List<BoundChangePoint> points,
            final List<JarDiffFailure> failures) {
        this(List.of(), points, failures);
    }
}
