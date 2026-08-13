package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;

import java.util.Objects;

// Wiki: wiki/features/bytecode-diff-engine.md - Immutable Module binding
/** Binds one immutable ChangePoint to Module-specific upgrade provenance. */
public final class BoundChangePoint {

    /** Dependency resolution identity. */
    private final DependencyUpgradeKey dependencyUpgradeKey;

    /** Coordinate-pair JAR diff result. */
    private final ChangePoint changePoint;

    /**
     * Creates a bound ChangePoint.
     *
     * @param key dependency upgrade identity
     * @param point coordinate-pair diff result
     */
    public BoundChangePoint(
            final DependencyUpgradeKey key,
            final ChangePoint point) {
        dependencyUpgradeKey = Objects.requireNonNull(key, "key");
        changePoint = Objects.requireNonNull(point, "changePoint");
        if (!dependencyUpgradeKey.getNewArtifact().equals(
                changePoint.getArtifact())) {
            throw new IllegalArgumentException(
                    "ChangePoint artifact must equal target artifact: point="
                            + changePoint.getArtifact() + ", target="
                            + dependencyUpgradeKey.getNewArtifact());
        }
    }

    /** @return dependency upgrade identity */
    public DependencyUpgradeKey getDependencyUpgradeKey() {
        return dependencyUpgradeKey;
    }

    /** @return bound ChangePoint */
    public ChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return deterministic identity */
    public String stableKey() {
        return dependencyUpgradeKey.stableKey() + ":"
                + changePoint.getKind() + ":"
                + changePoint.getOwner() + ":"
                + String.valueOf(changePoint.getName()) + ":"
                + String.valueOf(changePoint.getOldDescriptor()) + "->"
                + String.valueOf(changePoint.getNewDescriptor()) + ":"
                + changePoint.getAccessTransition()
                        .map(value -> value.stableKey())
                        .orElse("NO_ACCESS_TRANSITION") + ":"
                + changePoint.getServiceRegistration()
                        .map(value -> value.stableKey())
                        .orElse("NO_SERVICE_REGISTRATION");
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BoundChangePoint)) {
            return false;
        }
        final BoundChangePoint that = (BoundChangePoint) other;
        return dependencyUpgradeKey.equals(that.dependencyUpgradeKey)
                && changePoint.equals(that.changePoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependencyUpgradeKey, changePoint);
    }
}
