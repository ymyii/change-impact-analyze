package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;

import java.util.Objects;

/** ChangePoint rebound to one module-specific dependency resolution. */
public final class BoundChangePoint {

    /** Dependency resolution identity. */
    private final DependencyUpgradeKey dependencyUpgradeKey;

    /** Physical JAR diff result. */
    private final ChangePoint changePoint;

    /**
     * Creates a bound ChangePoint.
     *
     * @param key dependency upgrade identity
     * @param point physical diff result
     */
    public BoundChangePoint(
            final DependencyUpgradeKey key,
            final ChangePoint point) {
        dependencyUpgradeKey = Objects.requireNonNull(key, "key");
        changePoint = Objects.requireNonNull(point, "changePoint");
    }

    /** @return dependency upgrade identity */
    public DependencyUpgradeKey getDependencyUpgradeKey() {
        return dependencyUpgradeKey;
    }

    /** @return physical ChangePoint */
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
                + String.valueOf(changePoint.getNewDescriptor());
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
