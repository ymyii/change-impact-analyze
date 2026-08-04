package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import java.nio.file.Path;
import java.util.Objects;

/** Module-bound physical dependency upgrade identity. */
public final class DependencyUpgradeKey {

    /** Owning module. */
    private final ModuleId moduleId;

    /** Resolved dependency scope. */
    private final DependencyScope scope;

    /** Baseline artifact. */
    private final ArtifactCoord oldArtifact;

    /** Target artifact. */
    private final ArtifactCoord newArtifact;

    /** Canonical baseline artifact path. */
    private final Path oldPath;

    /** Canonical target artifact path. */
    private final Path newPath;

    /**
     * Creates an upgrade key.
     *
     * @param module owning module
     * @param dependencyScope dependency scope
     * @param oldValue old artifact
     * @param newValue new artifact
     * @param oldArtifactPath canonical old path
     * @param newArtifactPath canonical new path
     */
    public DependencyUpgradeKey(
            final ModuleId module,
            final DependencyScope dependencyScope,
            final ArtifactCoord oldValue,
            final ArtifactCoord newValue,
            final Path oldArtifactPath,
            final Path newArtifactPath) {
        moduleId = Objects.requireNonNull(module, "moduleId");
        scope = Objects.requireNonNull(dependencyScope, "scope");
        oldArtifact = Objects.requireNonNull(oldValue, "oldArtifact");
        newArtifact = Objects.requireNonNull(newValue, "newArtifact");
        oldPath = Objects.requireNonNull(oldArtifactPath, "oldPath");
        newPath = Objects.requireNonNull(newArtifactPath, "newPath");
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

    /** @return canonical old artifact path */
    public Path getOldPath() {
        return oldPath;
    }

    /** @return canonical new artifact path */
    public Path getNewPath() {
        return newPath;
    }

    /** @return stable module and artifact key */
    public String stableKey() {
        return moduleId.stableKey() + ":" + scope + ":"
                + oldArtifact + "->" + newArtifact + ":"
                + oldPath + "->" + newPath;
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
                && newArtifact.equals(that.newArtifact)
                && oldPath.equals(that.oldPath)
                && newPath.equals(that.newPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleId, scope, oldArtifact,
                newArtifact, oldPath, newPath);
    }
}
