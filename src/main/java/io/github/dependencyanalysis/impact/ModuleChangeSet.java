package io.github.dependencyanalysis.impact;

import java.util.List;

/**
 * Module-bound bytecode changes and isolated physical JAR diff failures.
 *
 * @param changePoints module-bound ChangePoints
 * @param jarDiffFailures physical JAR diff failure evidence
 */
public record ModuleChangeSet(
        List<BoundChangePoint> changePoints,
        List<String> jarDiffFailures) {

    /**
     * Creates an immutable deterministic module change set.
     *
     * @param changePoints module-bound ChangePoints
     * @param jarDiffFailures physical JAR diff failure evidence
     */
    public ModuleChangeSet {
        changePoints = List.copyOf(changePoints);
        jarDiffFailures = List.copyOf(jarDiffFailures);
    }
}
