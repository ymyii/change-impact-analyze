package io.github.changeimpact.analyze.dependency;

import java.util.Objects;

// Wiki: wiki/features/dependency-diff-engine.md - 单条依赖变动不可变数据类
/**
 * Immutable record of a single
 * dependency change between baseline
 * and target resolved trees.
 */
public final class DependencyChange {

    /** Change type. */
    private final ChangeType changeType;

    /** Old artifact (null for ADDED). */
    private final ArtifactCoord oldArtifact;

    /** New artifact (null for REMOVED). */
    private final ArtifactCoord newArtifact;

    /** Dependency scope. */
    private final DependencyScope scope;

    /** Module identifier string. */
    private final String module;

    /**
     * Creates a new dependency change.
     *
     * @param type change type
     * @param oldArt old artifact or null
     * @param newArt new artifact or null
     * @param scp dependency scope
     * @param mod module identifier
     */
    public DependencyChange(
            final ChangeType type,
            final ArtifactCoord oldArt,
            final ArtifactCoord newArt,
            final DependencyScope scp,
            final String mod) {
        this.changeType =
                Objects.requireNonNull(
                        type, "changeType");
        this.scope =
                Objects.requireNonNull(
                        scp, "scope");
        this.module =
                Objects.requireNonNull(
                        mod, "module");
        validate(type, oldArt, newArt);
        this.oldArtifact = oldArt;
        this.newArtifact = newArt;
    }

    /**
     * Validates artifact nullity
     * against change type.
     *
     * @param type change type
     * @param oldArt old artifact
     * @param newArt new artifact
     */
    private static void validate(
            final ChangeType type,
            final ArtifactCoord oldArt,
            final ArtifactCoord newArt) {
        switch (type) {
            case ADDED:
                if (newArt == null) {
                    throw new IllegalArgumentException(
                            "ADDED requires"
                                    + " newArtifact");
                }
                if (oldArt != null) {
                    throw new IllegalArgumentException(
                            "ADDED forbids"
                                    + " oldArtifact");
                }
                break;
            case REMOVED:
                if (oldArt == null) {
                    throw new IllegalArgumentException(
                            "REMOVED requires"
                                    + " oldArtifact");
                }
                if (newArt != null) {
                    throw new IllegalArgumentException(
                            "REMOVED forbids"
                                    + " newArtifact");
                }
                break;
            case VERSION_CHANGED:
                if (oldArt == null
                        || newArt == null) {
                    throw new IllegalArgumentException(
                            "VERSION_CHANGED"
                                    + " requires both"
                                    + " artifacts");
                }
                break;
            default:
                break;
        }
    }

    /**
     * Returns the change type.
     *
     * @return change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Returns the old artifact.
     * Null for ADDED changes.
     *
     * @return old artifact or null
     */
    public ArtifactCoord getOldArtifact() {
        return oldArtifact;
    }

    /**
     * Returns the new artifact.
     * Null for REMOVED changes.
     *
     * @return new artifact or null
     */
    public ArtifactCoord getNewArtifact() {
        return newArtifact;
    }

    /**
     * Returns the dependency scope.
     *
     * @return scope
     */
    public DependencyScope getScope() {
        return scope;
    }

    /**
     * Returns the module identifier.
     *
     * @return module string
     */
    public String getModule() {
        return module;
    }

    /**
     * Returns true if scope is PROVIDED,
     * indicating compile-time or API
     * risk.
     *
     * @return true if API risk
     */
    public boolean isCompileTimeApiRisk() {
        return scope
                == DependencyScope.PROVIDED;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyChange)) {
            return false;
        }
        final DependencyChange that =
                (DependencyChange) o;
        return changeType == that.changeType
                && Objects.equals(
                        oldArtifact,
                        that.oldArtifact)
                && Objects.equals(
                        newArtifact,
                        that.newArtifact)
                && scope == that.scope
                && module.equals(that.module);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                changeType, oldArtifact,
                newArtifact, scope, module);
    }

    @Override
    public String toString() {
        return "DependencyChange{"
                + "changeType=" + changeType
                + ", oldArtifact="
                + oldArtifact
                + ", newArtifact="
                + newArtifact
                + ", scope=" + scope
                + ", module=" + module
                + '}';
    }
}
