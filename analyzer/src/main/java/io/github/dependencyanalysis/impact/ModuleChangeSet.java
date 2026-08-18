package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.DecompileComparisonSummary;
import io.github.dependencyanalysis.bytecode.ServiceLoaderResourceIssue;
import io.github.dependencyanalysis.bytecode.ServiceProviderRegistration;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.dependency.DependencyChange;

import java.util.List;

/**
 * Module-bound bytecode changes and isolated coordinate-pair JAR diff failures.
 *
 * @param dependencyChanges complete module dependency changes
 * @param changePoints module-bound ChangePoints
 * @param jarDiffFailures coordinate-pair JAR diff failure evidence
 * @param baselineServiceRegistrations valid baseline provider facts
 * @param removedServiceRegistrations removed provider facts
 * @param serviceLoaderResourceIssues non-fatal resource Diff issues
 * @param ssaComparisons ChangePoint-collection normalized SSA evidence
 * @param decompileComparisons source-free decompiled Java evidence
 */
public record ModuleChangeSet(
        List<DependencyChange> dependencyChanges,
        List<BoundChangePoint> changePoints,
        List<JarDiffFailure> jarDiffFailures,
        List<ServiceProviderRegistration> baselineServiceRegistrations,
        List<ServiceProviderRegistration> removedServiceRegistrations,
        List<ServiceLoaderResourceIssue> serviceLoaderResourceIssues,
        List<SsaComparisonEvidence> ssaComparisons,
        List<DecompileComparisonSummary> decompileComparisons) {

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
        baselineServiceRegistrations = List.copyOf(
                baselineServiceRegistrations);
        removedServiceRegistrations = List.copyOf(
                removedServiceRegistrations);
        serviceLoaderResourceIssues = List.copyOf(
                serviceLoaderResourceIssues);
        ssaComparisons = List.copyOf(ssaComparisons);
        decompileComparisons = List.copyOf(decompileComparisons);
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
        this(List.of(), points, failures, List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    /** Compatibility constructor without ServiceLoader resource facts. */
    public ModuleChangeSet(
            final List<DependencyChange> dependencies,
            final List<BoundChangePoint> points,
            final List<JarDiffFailure> failures) {
        this(dependencies, points, failures,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Creates a change set without decompiled Java comparison evidence. */
    public ModuleChangeSet(
            final List<DependencyChange> dependencies,
            final List<BoundChangePoint> points,
            final List<JarDiffFailure> failures,
            final List<ServiceProviderRegistration> baselineRegistrations,
            final List<ServiceProviderRegistration> removedRegistrations,
            final List<ServiceLoaderResourceIssue> resourceIssues,
            final List<SsaComparisonEvidence> comparisons) {
        this(dependencies, points, failures, baselineRegistrations,
                removedRegistrations, resourceIssues, comparisons,
                List.of());
    }
}
