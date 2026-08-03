package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Structured evidence for one failed physical JAR comparison.
 *
 * @param dependencyUpgradeKey dependency upgrade identity
 * @param reason failure reason
 */
public record JarDiffFailure(
        DependencyUpgradeKey dependencyUpgradeKey,
        String reason) {

    /** Validates required evidence. */
    public JarDiffFailure {
        Objects.requireNonNull(dependencyUpgradeKey,
                "dependencyUpgradeKey");
        Objects.requireNonNull(reason, "reason");
    }

    /** @return stable report ordering key */
    public String stableKey() {
        return dependencyUpgradeKey.stableKey() + ":" + reason;
    }

    /** @return concise plain diagnostic */
    public String summary() {
        return "JAR comparison failed for "
                + dependencyUpgradeKey.getOldArtifact() + " -> "
                + dependencyUpgradeKey.getNewArtifact() + ": " + reason;
    }
}
