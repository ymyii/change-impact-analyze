package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import java.util.Objects;

/** Module-bound logical dependency upgrade identity. */
public final class DependencyUpgradeKey {

    /** Owning module. */
    private final ModuleId moduleId;

    /** Resolved dependency scope. */
    private final DependencyScope scope;

    /** Baseline artifact. */
    private final ArtifactCoord oldArtifact;

    /** Target artifact. */
    private final ArtifactCoord newArtifact;

    /**
     * Creates an upgrade key.
     *
     * @param module owning module
     * @param dependencyScope dependency scope
     * @param oldValue old artifact
     * @param newValue new artifact
     */
    public DependencyUpgradeKey(
            final ModuleId module,
            final DependencyScope dependencyScope,
            final ArtifactCoord oldValue,
            final ArtifactCoord newValue) {
        moduleId = Objects.requireNonNull(module, "moduleId");
        scope = Objects.requireNonNull(dependencyScope, "scope");
        oldArtifact = Objects.requireNonNull(oldValue, "oldArtifact");
        newArtifact = Objects.requireNonNull(newValue, "newArtifact");
    }

    /** @return owning module */
    public ModuleId getModuleId() {
        return moduleId;
    }

    /** @return dependency scope */
    public DependencyScope getScope() {
        return scope;
    }

    /** @return old artifact */
    public ArtifactCoord getOldArtifact() {
        return oldArtifact;
    }

    /** @return new artifact */
    public ArtifactCoord getNewArtifact() {
        return newArtifact;
    }

    /** @return stable module and artifact key */
    public String stableKey() {
        return moduleId.stableKey() + ":" + scope + ":"
                + oldArtifact + "->" + newArtifact;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DependencyUpgradeKey)) {
            return false;
        }
        final DependencyUpgradeKey that = (DependencyUpgradeKey) other;
        return moduleId.equals(that.moduleId)
                && scope == that.scope
                && oldArtifact.equals(that.oldArtifact)
                && newArtifact.equals(that.newArtifact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleId, scope, oldArtifact, newArtifact);
    }
}
