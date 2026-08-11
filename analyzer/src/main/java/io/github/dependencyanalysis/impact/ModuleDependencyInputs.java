package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** External dependency inputs derived from merged Module evidence. */
public final class ModuleDependencyInputs {

    /** Target external classpath. */
    private final List<ArtifactCoord> targetArtifacts;

    /** Baseline external classpath. */
    private final List<ArtifactCoord> baselineArtifacts;

    /** Target dependency path selection. */
    private final ModuleChangedPathSelection changedPathSelection;

    private ModuleDependencyInputs(
            final List<ArtifactCoord> target,
            final List<ArtifactCoord> baseline,
            final ModuleChangedPathSelection selection) {
        targetArtifacts = List.copyOf(Objects.requireNonNull(
                target, "target"));
        baselineArtifacts = List.copyOf(Objects.requireNonNull(
                baseline, "baseline"));
        changedPathSelection = Objects.requireNonNull(
                selection, "selection");
    }

    /**
     * Creates inputs from baseline and target merged evidence.
     *
     * @param target target evidence, nullable for baseline-only Module
     * @param baseline baseline evidence, nullable for target-only Module
     * @param changedArtifacts target selected artifacts with ChangePoints
     * @param requested requested dependency analysis scope
     * @return same-source Module dependency inputs
     */
    public static ModuleDependencyInputs fromEvidence(
            final ModuleDependencyEvidence target,
            final ModuleDependencyEvidence baseline,
            final Set<ArtifactCoord> changedArtifacts,
            final DependencyAnalysisScopeMode requested) {
        final List<ArtifactCoord> targetArtifacts = target == null
                ? List.of() : ModuleClasspathOrder.externalArtifacts(target);
        final List<ArtifactCoord> baselineArtifacts = baseline == null
                ? List.of() : ModuleClasspathOrder.externalArtifacts(baseline);
        final ModuleChangedPathSelection selection = target == null
                ? ModuleChangedPathSelection.fullArtifacts(targetArtifacts)
                : ModuleChangedPathSelection.plan(target,
                changedArtifacts, requested);
        return new ModuleDependencyInputs(targetArtifacts,
                baselineArtifacts, selection);
    }

    static ModuleDependencyInputs fullArtifacts(
            final List<ArtifactCoord> target,
            final List<ArtifactCoord> baseline) {
        return new ModuleDependencyInputs(target, baseline,
                ModuleChangedPathSelection.fullArtifacts(target));
    }

    /** @return target external classpath */
    public List<ArtifactCoord> targetArtifacts() {
        return targetArtifacts;
    }

    /** @return baseline external classpath */
    public List<ArtifactCoord> baselineArtifacts() {
        return baselineArtifacts;
    }

    /** @return target dependency path selection */
    public ModuleChangedPathSelection changedPathSelection() {
        return changedPathSelection;
    }
}
