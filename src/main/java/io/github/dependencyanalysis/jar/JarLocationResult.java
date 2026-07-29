package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.DependencyChange;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Holds located old and new jar paths
 * for a single version-changed
 * dependency.
 */
public final class JarLocationResult {

    /** The version-changed change. */
    private final DependencyChange change;

    /** Path to the old jar file. */
    private final Path oldJar;

    /** Path to the new jar file. */
    private final Path newJar;

    /**
     * Creates a new jar location result.
     *
     * @param chg the dependency change
     * @param old path to old jar
     * @param nbr path to new jar
     */
    public JarLocationResult(
            final DependencyChange chg,
            final Path old,
            final Path nbr) {
        this.change =
                Objects.requireNonNull(
                        chg, "change");
        this.oldJar =
                Objects.requireNonNull(
                        old, "oldJar");
        this.newJar =
                Objects.requireNonNull(
                        nbr, "newJar");
    }

    /**
     * Returns the dependency change.
     *
     * @return dependency change
     */
    public DependencyChange getChange() {
        return change;
    }

    /**
     * Returns the old jar path.
     *
     * @return old jar path
     */
    public Path getOldJar() {
        return oldJar;
    }

    /**
     * Returns the new jar path.
     *
     * @return new jar path
     */
    public Path getNewJar() {
        return newJar;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JarLocationResult)) {
            return false;
        }
        final JarLocationResult that =
                (JarLocationResult) o;
        return change.equals(that.change)
                && oldJar.equals(that.oldJar)
                && newJar.equals(that.newJar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                change, oldJar, newJar);
    }

    @Override
    public String toString() {
        return "JarLocationResult{"
                + "change=" + change
                + ", oldJar=" + oldJar
                + ", newJar=" + newJar
                + '}';
    }
}
