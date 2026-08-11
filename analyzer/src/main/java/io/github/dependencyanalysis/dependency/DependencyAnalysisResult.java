package io.github.dependencyanalysis.dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Winner-normalized dependency evidence for every selected Module. */
public final class DependencyAnalysisResult {

    /** Merged Module dependency evidence. */
    private final List<ModuleDependencyEvidence> modules;

    /** Flattened physical artifact bindings derived from Module evidence. */
    private final List<ResolvedArtifact> artifacts;

    /**
     * Creates a dependency analysis result.
     *
     * @param moduleEvidence merged Module evidence
     */
    public DependencyAnalysisResult(
            final List<ModuleDependencyEvidence> moduleEvidence) {
        modules = List.copyOf(Objects.requireNonNull(
                moduleEvidence, "moduleEvidence"));
        final List<ResolvedArtifact> flattened = new ArrayList<>();
        modules.forEach(value -> flattened.addAll(value.getArtifacts()));
        artifacts = List.copyOf(flattened);
    }

    /** @return merged Module dependency evidence */
    public List<ModuleDependencyEvidence> getModules() {
        return modules;
    }

    /** @return physical artifact bindings derived from Module evidence */
    public List<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }
}
