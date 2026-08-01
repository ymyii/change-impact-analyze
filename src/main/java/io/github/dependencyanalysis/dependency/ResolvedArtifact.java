package io.github.dependencyanalysis.dependency;

import java.nio.file.Path;
import java.util.Objects;

/** Physical dependency artifact resolved for one Maven module. */
public final class ResolvedArtifact {

    /** Owning Maven module directory. */
    private final Path modulePath;

    /** Resolved artifact coordinate. */
    private final ArtifactCoord artifact;

    /** Effective dependency scope. */
    private final DependencyScope scope;

    /** Canonical physical path. */
    private final Path path;

    /**
     * Creates a resolved artifact.
     *
     * @param module owning module directory
     * @param value artifact coordinate
     * @param dependencyScope effective scope
     * @param physicalPath canonical physical path
     */
    public ResolvedArtifact(
            final Path module,
            final ArtifactCoord value,
            final DependencyScope dependencyScope,
            final Path physicalPath) {
        modulePath = Objects.requireNonNull(module, "modulePath")
                .toAbsolutePath().normalize();
        artifact = Objects.requireNonNull(value, "artifact");
        scope = Objects.requireNonNull(dependencyScope, "scope");
        path = Objects.requireNonNull(physicalPath, "path")
                .toAbsolutePath().normalize();
    }

    /** @return owning module directory */
    public Path getModulePath() {
        return modulePath;
    }

    /** @return artifact coordinate */
    public ArtifactCoord getArtifact() {
        return artifact;
    }

    /** @return effective dependency scope */
    public DependencyScope getScope() {
        return scope;
    }

    /** @return canonical physical path */
    public Path getPath() {
        return path;
    }

    /** @return coordinate and scope identity */
    public String bindingKey() {
        return artifact + ":" + scope.getValue();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedArtifact)) {
            return false;
        }
        final ResolvedArtifact that = (ResolvedArtifact) other;
        return modulePath.equals(that.modulePath)
                && artifact.equals(that.artifact)
                && scope == that.scope
                && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modulePath, artifact, scope, path);
    }
}
