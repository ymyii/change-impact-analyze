package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.classpath.ClassConflictRisk;

import java.util.List;
import java.util.Objects;

/**
 * User-facing resolution evidence for one Module-class relation.
 *
 * @param binaryName internal binary class name
 * @param risk digest-derived risk
 * @param winner selected candidate
 * @param candidates all candidates in classpath order
 * @param selection stable selection reason
 */
public record TreeClassConflict(
        String binaryName,
        ClassConflictRisk risk,
        TreeClassConflictCandidate winner,
        List<TreeClassConflictCandidate> candidates,
        String selection) {

    /** Validates immutable conflict evidence. */
    public TreeClassConflict {
        Objects.requireNonNull(binaryName, "binaryName");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(winner, "winner");
        candidates = List.copyOf(candidates);
        if (!candidates.contains(winner) || candidates.size() < 2) {
            throw new IllegalArgumentException(
                    "Conflict requires winner among two or more candidates");
        }
        Objects.requireNonNull(selection, "selection");
    }

    /** @return Java binary name */
    public String displayName() {
        return binaryName.replace('/', '.');
    }

    /** @return shadowed candidates in classpath order */
    public List<TreeClassConflictCandidate> shadowed() {
        return candidates.stream().filter(value -> value != winner).toList();
    }
}
