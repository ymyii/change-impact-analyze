package io.github.changeimpact.analyze.build;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable record of a single Maven
 * module build output paths.
 */
public final class ModuleBuildOutput {

    /** Root directory of the module. */
    private final Path modulePath;

    /** Path to target/classes directory. */
    private final Path classesDir;

    /**
     * Creates a new module build output.
     *
     * @param module  module root path
     * @param classes target/classes path
     */
    public ModuleBuildOutput(
            final Path module,
            final Path classes) {
        this.modulePath =
                Objects.requireNonNull(
                        module, "modulePath");
        this.classesDir =
                Objects.requireNonNull(
                        classes, "classesDir");
    }

    /**
     * Returns the module root path.
     *
     * @return module path
     */
    public Path getModulePath() {
        return modulePath;
    }

    /**
     * Returns the target/classes path.
     *
     * @return classes directory path
     */
    public Path getClassesDir() {
        return classesDir;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModuleBuildOutput)) {
            return false;
        }
        final ModuleBuildOutput that =
                (ModuleBuildOutput) o;
        return modulePath.equals(
                that.modulePath)
                && classesDir.equals(
                        that.classesDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modulePath, classesDir);
    }

    @Override
    public String toString() {
        return "ModuleBuildOutput{"
                + "modulePath=" + modulePath
                + ", classesDir=" + classesDir
                + '}';
    }
}
